package com.example.ragkb.chat.repository;

public interface MessageReferenceDetail {

    Long getMessageId();

    Long getDocumentId();

    Long getChunkId();

    int getChunkIndex();

    String getContent();

    double getSimilarity();

    String getFileName();

    Integer getPageNumber();

    Integer getPdfPageNumber();
}
