package com.smartorganizer.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class DuplicateRow {
    private final int groupNumber;
    private final FileInfo file;
    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    public DuplicateRow(int groupNumber, FileInfo file) {
        this.groupNumber = groupNumber;
        this.file = file;
    }

    public int getGroupNumber() {
        return groupNumber;
    }

    public FileInfo getFile() {
        return file;
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }
}