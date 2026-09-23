package com.smartorganizer.model;

import java.util.List;

public record ScanResult(List<FileInfo> files, int foldersScanned, int skippedCount) {
}