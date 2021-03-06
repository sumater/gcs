/*
 * Copyright (c) 1998-2017 by Richard A. Wilkes. All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, version 2.0. If a copy of the MPL was not distributed with
 * this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, version 2.0.
 */

package com.trollworks.gcs.spell;

import com.trollworks.gcs.character.GURPSCharacter;
import com.trollworks.gcs.common.DataFile;
import com.trollworks.gcs.common.ListFile;
import com.trollworks.gcs.library.LibraryFile;
import com.trollworks.gcs.menu.edit.Incrementable;
import com.trollworks.gcs.menu.edit.SkillLevelIncrementable;
import com.trollworks.gcs.menu.edit.TechLevelIncrementable;
import com.trollworks.gcs.template.Template;
import com.trollworks.gcs.widgets.outline.ListOutline;
import com.trollworks.gcs.widgets.outline.ListRow;
import com.trollworks.gcs.widgets.outline.MultipleRowUndo;
import com.trollworks.gcs.widgets.outline.RowPostProcessor;
import com.trollworks.gcs.widgets.outline.RowUndo;
import com.trollworks.toolkit.annotation.Localize;
import com.trollworks.toolkit.collections.FilteredIterator;
import com.trollworks.toolkit.ui.widget.outline.OutlineModel;
import com.trollworks.toolkit.ui.widget.outline.Row;
import com.trollworks.toolkit.utility.Localization;
import com.trollworks.toolkit.utility.text.Numbers;

import java.awt.EventQueue;
import java.awt.dnd.DropTargetDragEvent;
import java.util.ArrayList;
import java.util.List;

/** An outline specifically for spells. */
public class SpellOutline extends ListOutline implements Incrementable, TechLevelIncrementable, SkillLevelIncrementable {
    @Localize("Increment Points")
    @Localize(locale = "de", value = "Punkte erhöhen")
    @Localize(locale = "ru", value = "Увеличить очки")
    @Localize(locale = "es", value = "Incrementar Puntos")
    private static String INCREMENT;
    @Localize("Decrement Points")
    @Localize(locale = "de", value = "Punkte verringern")
    @Localize(locale = "ru", value = "Уменьшить очки")
    @Localize(locale = "es", value = "Disminuir Puntos")
    private static String DECREMENT;
    @Localize("Increment Spell Level")
    @Localize(locale = "ru", value = "Увеличить уровень заклинания")
    private static String INCREMENT_SPELL_LEVEL;
    @Localize("Decrement Spell Level")
    @Localize(locale = "ru", value = "Уменьшить уровень заклинания")
    private static String DECREMENT_SPELL_LEVEL;

    static {
        Localization.initialize();
    }

    private static OutlineModel extractModel(DataFile dataFile) {
        if (dataFile instanceof GURPSCharacter) {
            return ((GURPSCharacter) dataFile).getSpellsRoot();
        }
        if (dataFile instanceof Template) {
            return ((Template) dataFile).getSpellsModel();
        }
        if (dataFile instanceof LibraryFile) {
            return ((LibraryFile) dataFile).getSpellList().getModel();
        }
        return ((ListFile) dataFile).getModel();
    }

    /**
     * Create a new spells outline.
     *
     * @param dataFile The owning data file.
     */
    public SpellOutline(DataFile dataFile) {
        this(dataFile, extractModel(dataFile));
    }

    /**
     * Create a new spells outline.
     *
     * @param dataFile The owning data file.
     * @param model    The {@link OutlineModel} to use.
     */
    public SpellOutline(DataFile dataFile, OutlineModel model) {
        super(dataFile, model, Spell.ID_LIST_CHANGED);
        SpellColumn.addColumns(this, dataFile);
    }

    @Override
    public String getDecrementTitle() {
        return DECREMENT;
    }

    @Override
    public String getIncrementTitle() {
        return INCREMENT;
    }

    @Override
    public boolean canDecrement() {
        return (mDataFile instanceof GURPSCharacter || mDataFile instanceof Template) && selectionHasLeafRows(true);
    }

    @Override
    public boolean canIncrement() {
        return (mDataFile instanceof GURPSCharacter || mDataFile instanceof Template) && selectionHasLeafRows(false);
    }

    private boolean selectionHasLeafRows(boolean requirePointsAboveZero) {
        for (Spell spell : new FilteredIterator<>(getModel().getSelectionAsList(), Spell.class)) {
            if (!spell.canHaveChildren() && (!requirePointsAboveZero || spell.getPoints() > 0)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unused")
    @Override
    public void decrement() {
        List<RowUndo> undos = new ArrayList<RowUndo>();
        for (Spell spell : new FilteredIterator<>(getModel().getSelectionAsList(), Spell.class)) {
            if (!spell.canHaveChildren()) {
                int points = spell.getPoints();
                if (points > 0) {
                    RowUndo undo = new RowUndo(spell);
                    spell.setPoints(points - 1);
                    if (undo.finish()) {
                        undos.add(undo);
                    }
                }
            }
        }
        if (!undos.isEmpty()) {
            repaintSelection();
            new MultipleRowUndo(undos);
        }
    }

    @SuppressWarnings("unused")
    @Override
    public void increment() {
        List<RowUndo> undos = new ArrayList<RowUndo>();
        for (Spell spell : new FilteredIterator<Spell>(getModel().getSelectionAsList(), Spell.class)) {
            if (!spell.canHaveChildren()) {
                RowUndo undo = new RowUndo(spell);
                spell.setPoints(spell.getPoints() + 1);
                if (undo.finish()) {
                    undos.add(undo);
                }
            }
        }
        if (!undos.isEmpty()) {
            repaintSelection();
            new MultipleRowUndo(undos);
        }
    }

    @Override
    public String getIncrementSkillLevelTitle() {
        return INCREMENT_SPELL_LEVEL;
    }

    @Override
    public String getDecrementSkillLevelTitle() {
        return DECREMENT_SPELL_LEVEL;
    }

    @Override
    public boolean canIncrementSkillLevel() {
        return canIncrement();
    }

    @Override
    public boolean canDecrementSkillLevel() {
        return canDecrement();
    }

    @SuppressWarnings("unused")
    @Override
    public void incrementSkillLevel() {
        List<RowUndo> undos = new ArrayList<>();
        for (Spell spell : new FilteredIterator<>(getModel().getSelectionAsList(), Spell.class)) {
            if (!spell.canHaveChildren()) {
                int     basePoints = spell.getPoints() + 1;
                int     maxPoints  = basePoints + 4;
                int     oldLevel   = spell.getLevel();
                RowUndo undo       = new RowUndo(spell);
                for (int points = basePoints; points < maxPoints; points++) {
                    spell.setPoints(points);
                    if (spell.getLevel() > oldLevel) {
                        break;
                    }
                }
                // if skill level didn't change, perhaps we hit the limit and should reset points to
                // old value
                if (spell.getLevel() == oldLevel) {
                    spell.setPoints(basePoints - 1);
                }
                if (undo.finish()) {
                    undos.add(undo);
                }
            }
        }
        if (!undos.isEmpty()) {
            repaintSelection();
            new MultipleRowUndo(undos);
        }
    }

    @SuppressWarnings("unused")
    @Override
    public void decrementSkillLevel() {
        List<RowUndo> undos = new ArrayList<>();
        for (Spell spell : new FilteredIterator<>(getModel().getSelectionAsList(), Spell.class)) {
            if (!spell.canHaveChildren()) {
                RowUndo undo     = new RowUndo(spell);
                int     oldLevel = spell.getLevel();
                int     points   = spell.getPoints() - 1;
                spell.setPoints(points);
                if (spell.getLevel() == -1) {
                    spell.setPoints(0);
                } else {
                    while (points > 0) {
                        spell.setPoints(--points);
                        if (spell.getLevel() < oldLevel - 1) {
                            spell.setPoints(points + 1);
                            break;
                        }
                    }
                }
                if (undo.finish()) {
                    undos.add(undo);
                }
            }
        }
        if (!undos.isEmpty()) {
            repaintSelection();
            new MultipleRowUndo(undos);
        }
    }

    @Override
    public boolean canIncrementTechLevel() {
        return checkTechLevel(0);
    }

    @Override
    public boolean canDecrementTechLevel() {
        return checkTechLevel(1);
    }

    private boolean checkTechLevel(int minLevel) {
        for (Spell spell : new FilteredIterator<>(getModel().getSelectionAsList(), Spell.class)) {
            if (!spell.canHaveChildren() && getCurrentTechLevel(spell) >= minLevel) {
                return true;
            }
        }
        return false;
    }

    private static int getCurrentTechLevel(Spell spell) {
        String tl = spell.getTechLevel();
        if (tl != null) {
            tl = tl.trim();
            if (!tl.isEmpty()) {
                for (int i = tl.length(); --i >= 0;) {
                    if (!Character.isDigit(tl.charAt(i))) {
                        return -1;
                    }
                }
                return Numbers.extractInteger(tl, -1, 0, Integer.MAX_VALUE, false);
            }
        }
        return -1;
    }

    @SuppressWarnings("unused")
    @Override
    public void incrementTechLevel() {
        List<RowUndo> undos = new ArrayList<>();
        for (Spell spell : new FilteredIterator<>(getModel().getSelectionAsList(), Spell.class)) {
            if (!spell.canHaveChildren()) {
                int tl = getCurrentTechLevel(spell);
                if (tl >= 0) {
                    RowUndo undo = new RowUndo(spell);
                    spell.setTechLevel(Integer.toString(tl + 1));
                    if (undo.finish()) {
                        undos.add(undo);
                    }
                }
            }
        }
        if (!undos.isEmpty()) {
            repaintSelection();
            new MultipleRowUndo(undos);
        }
    }

    @SuppressWarnings("unused")
    @Override
    public void decrementTechLevel() {
        List<RowUndo> undos = new ArrayList<>();
        for (Spell spell : new FilteredIterator<>(getModel().getSelectionAsList(), Spell.class)) {
            if (!spell.canHaveChildren()) {
                int tl = getCurrentTechLevel(spell);
                if (tl >= 1) {
                    RowUndo undo = new RowUndo(spell);
                    spell.setTechLevel(Integer.toString(tl - 1));
                    if (undo.finish()) {
                        undos.add(undo);
                    }
                }
            }
        }
        if (!undos.isEmpty()) {
            repaintSelection();
            new MultipleRowUndo(undos);
        }
    }

    @Override
    protected boolean isRowDragAcceptable(DropTargetDragEvent dtde, Row[] rows) {
        return !getModel().isLocked() && rows.length > 0 && rows[0] instanceof Spell;
    }

    @Override
    public void convertDragRowsToSelf(List<Row> list) {
        OutlineModel       model              = getModel();
        Row[]              rows               = model.getDragRows();
        boolean            forSheetOrTemplate = mDataFile instanceof GURPSCharacter || mDataFile instanceof Template;
        ArrayList<ListRow> process            = new ArrayList<>();

        for (Row element : rows) {
            Spell spell = new Spell(mDataFile, (Spell) element, true, forSheetOrTemplate);

            model.collectRowsAndSetOwner(list, spell, false);
            if (forSheetOrTemplate) {
                addRowsToBeProcessed(process, spell);
            }
        }

        if (forSheetOrTemplate && !process.isEmpty()) {
            EventQueue.invokeLater(new RowPostProcessor(this, process));
        }
    }
}
