// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.util.NlsContexts.ColumnName;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.util.Comparator;
import java.util.Objects;

public abstract class ColumnInfo<Item, Aspect> {
  private @ColumnName String myName;
  public static final ColumnInfo[] EMPTY_ARRAY = new ColumnInfo[0];

  @SuppressWarnings("unchecked")
  public static <I, A> @NotNull ColumnInfo<I, A> @NotNull [] emptyArray() {
    return EMPTY_ARRAY;
  }

  public ColumnInfo(@ColumnName String name) {
    myName = name;
  }

  public @Nullable Icon getIcon() {
    return null;
  }

  public String toString() {
    return getName();
  }

  public abstract @Nullable Aspect valueOf(Item item);

  public final boolean isSortable() {
    return getComparator() != null;
  }

  public @Nullable Comparator<Item> getComparator(){
    return null;
  }

  public @ColumnName String getName() {
    return myName;
  }

  public Class<?> getColumnClass() {
    return String.class;
  }

  public boolean isCellEditable(Item item) {
    return false;
  }

  public void setValue(Item item, Aspect value) {

  }

  /**
   * @see com.intellij.util.ui.table.IconTableCellRenderer
   * @see com.intellij.util.ui.LocalPathCellEditor
   */
  public @Nullable TableCellRenderer getRenderer(Item item) {
    return null;
  }

  public TableCellRenderer getCustomizedRenderer(final Item o, TableCellRenderer renderer) {
    return renderer;
  }

  public @Nullable TableCellEditor getEditor(Item item) {
    return null;
  }

  public @Nullable String getMaxStringValue() {
    return null;
  }

  public @Nullable String getPreferredStringValue() {
    return null;
  }

  public int getAdditionalWidth() {
    return 0;
  }

  public int getWidth(JTable table) {
    return -1;
  }

  public void setName(@ColumnName String s) {
    myName = s;
  }

  public @Tooltip @Nullable String getTooltipText() {
    return null;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ColumnInfo that = (ColumnInfo)o;

    if (!Objects.equals(myName, that.myName)) return false;

    return true;
  }

  public int hashCode() {
    return myName != null ? myName.hashCode() : 0;
  }

  public boolean hasError() {
    return false;
  }
}
