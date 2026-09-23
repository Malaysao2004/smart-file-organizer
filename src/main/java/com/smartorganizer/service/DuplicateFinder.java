package com.smartorganizer.service;

import com.smartorganizer.model.DuplicateGroup;
import com.smartorganizer.model.FileInfo;
import com.smartorganizer.util.HashUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public class DuplicateFinder {

    public List<DuplicateGroup> find(List<FileInfo> files, DoubleConsumer progress,
                                     Consumer<String> log, BooleanSupplier cancelled) {
        // Step 1: group by size (empty files are ignored)
        Map<Long, List<FileInfo>> bySize = new HashMap<>();
        for (FileInfo f : files) {
            if (f.size() > 0) {
                bySize.computeIfAbsent(f.size(), k -> new ArrayList<>()).add(f);
            }
        }
        List<FileInfo> candidates = new ArrayList<>();
        for (List<FileInfo> group : bySize.values()) {
            if (group.size() > 1) {
                candidates.addAll(group);
            }
        }
        log.accept("Duplicate candidates (same size): " + candidates.size() + " files");

        // Step 2: SHA-256 only for candidates
        Map<String, List<FileInfo>> byHash = new HashMap<>();
        int done = 0;
        for (FileInfo f : candidates) {
            if (cancelled.getAsBoolean()) {
                break;
            }
            try {
                String hash = HashUtil.sha256(f.path());
                byHash.computeIfAbsent(hash, k -> new ArrayList<>()).add(f);
            } catch (IOException | RuntimeException e) {
                log.accept("Could not read file for hashing: " + f.path() + " - " + e.getMessage());
            }
            done++;
            progress.accept((double) done / candidates.size());
        }

        // Step 3: keep only hashes with 2+ files
        List<DuplicateGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<FileInfo>> e : byHash.entrySet()) {
            if (e.getValue().size() > 1) {
                result.add(new DuplicateGroup(e.getKey(), List.copyOf(e.getValue())));
            }
        }
        result.sort(Comparator.comparingLong(DuplicateGroup::wastedBytes).reversed());
        return result;
    }
}