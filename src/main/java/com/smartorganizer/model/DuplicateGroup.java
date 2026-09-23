package com.smartorganizer.model;

import java.util.List;

public record DuplicateGroup(String hash, List<FileInfo> files) {

    public long fileSize() {
        return files.get(0).size();
    }

    /** Space that can be freed if only one copy is kept. */
    public long wastedBytes() {
        return fileSize() * (files.size() - 1);
    }
}