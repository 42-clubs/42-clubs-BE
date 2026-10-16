package com.clubplatform.activity.storage;

public interface PhotoStorage {
    void put(String key, byte[] bytes, String contentType);

    void delete(String key);

    String url(String key);
}
