#!/usr/bin/env python3
"""Integration checks in a disposable, unexposed PostgreSQL 17 container."""

import importlib.util
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import time
import unittest
import uuid

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('seed_demo', Path(__file__).with_name('seed_demo.py'))
seed = importlib.util.module_from_spec(spec)
spec.loader.exec_module(seed)


class DemoSeedTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.container = 'clubs-seed-test-' + uuid.uuid4().hex[:12]
        subprocess.run([
            'docker', 'run', '--detach', '--rm', '--network', 'none',
            '--name', cls.container, '-e', 'POSTGRES_HOST_AUTH_METHOD=trust',
            'postgres:17.6-alpine',
        ], check=True, stdout=subprocess.DEVNULL)
        cls.addClassCleanup(subprocess.run, ['docker', 'stop', cls.container],
                            check=False, stdout=subprocess.DEVNULL)
        for _ in range(60):
            result = subprocess.run(['docker', 'exec', cls.container, 'pg_isready', '-h', '127.0.0.1', '-U', 'postgres'],
                                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if result.returncode == 0:
                break
            time.sleep(0.5)
        else:
            raise RuntimeError('Test PostgreSQL did not become ready')

    def sql(self, sql, check=True, database=None):
        return subprocess.run([
            'docker', 'exec', '-i', self.container, 'psql', '-X', '-qAt',
            '-v', 'ON_ERROR_STOP=1', '-U', 'postgres', '-d', database or self.database,
        ], input=sql, text=True, capture_output=True, check=check)

    def setUp(self):
        self.database = 'seed_' + uuid.uuid4().hex
        self.sql('CREATE DATABASE ' + self.database, database='postgres')
        migrations = sorted(
            (seed.ROOT / 'src/main/resources/db/migration').glob('V*.sql'),
            key=lambda p: int(p.name.split('__')[0][1:]),
        )
        self.sql('\n'.join(p.read_text() for p in migrations))

    def test_repeated_seed_preserves_rows_ids_times_and_existing_data(self):
        self.sql("INSERT INTO users (intra_id,intra_login,display_name,role,created_at,updated_at) "
                 "VALUES (42,'real_user','Existing user','USER',now(),now());"
                 "INSERT INTO clubs (name,summary,description,operation_status,created_at,updated_at) "
                 "VALUES ('Existing club','Keep','Original content','ACTIVE',now(),now());")
        self.sql(seed.build_sql())
        tables = [
            'users', 'clubs', 'club_managers', 'club_members', 'activities', 'activity_images',
            'activity_participants', 'notices', 'announcements', 'gatherings',
            'gathering_sessions', 'gathering_session_participants',
        ]
        snapshot = lambda: {t: self.sql('SELECT row_to_json(t) FROM ' + t + ' t ORDER BY id').stdout
                            for t in tables}
        before = snapshot()
        self.sql(seed.build_sql())
        self.assertEqual(before, snapshot())
        counts = {t: int(self.sql('SELECT count(*) FROM ' + t).stdout) for t in tables}
        self.assertEqual([counts[t] for t in ('users', 'clubs', 'activities', 'activity_images',
                                              'gatherings', 'gathering_sessions')],
                         [13, 9, 15, 15, 8, 16])
        self.assertEqual(self.sql("SELECT description FROM clubs WHERE name='Existing club'").stdout.strip(),
                         'Original content')
        self.assertEqual(self.sql("SELECT role FROM users WHERE intra_id=42").stdout.strip(), 'USER')
        self.assertEqual(self.sql(
            "SELECT count(*) FROM gathering_sessions s WHERE s.capacity < "
            "(SELECT count(*) FROM gathering_session_participants p WHERE p.session_id=s.id "
            "AND p.status='CONFIRMED')").stdout.strip(), '0')
        self.assertEqual(self.sql(
            "SELECT count(*) FROM activities a WHERE NOT a.allow_external_participants AND EXISTS "
            "(SELECT 1 FROM activity_participants p WHERE p.activity_id=a.id AND NOT EXISTS "
            "(SELECT 1 FROM club_members m WHERE m.club_id=a.club_id AND m.user_id=p.user_id "
            "AND m.status='ACTIVE'))").stdout.strip(), '0')

    def test_reserved_identity_collision_rolls_back(self):
        self.sql("INSERT INTO users (intra_id,intra_login,display_name,role,created_at,updated_at) "
                 "VALUES (-420001,'someone_else','Keep','USER',now(),now());")
        self.assertNotEqual(self.sql(seed.build_sql(), check=False).returncode, 0)
        self.assertEqual(self.sql('SELECT count(*) FROM users').stdout.strip(), '1')
        self.assertEqual(self.sql('SELECT count(*) FROM clubs').stdout.strip(), '0')

    def test_club_collision_rolls_back_every_new_user(self):
        self.sql("INSERT INTO clubs (name,summary,description,operation_status,created_at,updated_at) "
                 "VALUES ('[DEMO] 42 Music','Keep','Not seed owned','ACTIVE',now(),now());")
        self.assertNotEqual(self.sql(seed.build_sql(), check=False).returncode, 0)
        self.assertEqual(self.sql('SELECT count(*) FROM users').stdout.strip(), '0')
        self.assertEqual(self.sql('SELECT count(*) FROM clubs').stdout.strip(), '1')

    def test_images_have_valid_png_headers_and_match_sql_keys(self):
        covers = seed.ROOT.parent / '42-clubs-FE/public/covers'
        with tempfile.TemporaryDirectory() as output:
            output = Path(output)
            sql = seed.prepare(covers, output)
            files = list((output / 'activities').glob('*.png'))
            self.assertEqual(len(files), 15)
            for file in files:
                data = file.read_bytes()
                self.assertEqual(data[:8], b'\x89PNG\r\n\x1a\n')
                width, height = struct.unpack('>II', data[16:24])
                self.assertTrue(0 < width * height <= 25000000)
                self.assertLessEqual(len(data), 5242880)
                self.assertIn('activities/' + file.name, sql)
            self.sql(sql)


if __name__ == '__main__':
    unittest.main(verbosity=2)
