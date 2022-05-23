package com.github.phillco.talonjetbrains.cursorless;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class CursorlessContainer extends JComponent {
    private Editor editor;
    private JComponent parent;

    public static String HATS_PATH = System.getProperty("user.home") + "/.vscode-hats.json";

    public CursorlessContainer(Editor editor) {
        this.editor = editor;
        this.parent = editor.getContentComponent();

        this.parent.add(this);
        this.setBounds(parent.getBounds());
        setVisible(true);
        System.out.println("cursorless container initialized!");
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (!new File(HATS_PATH).exists()) {
            System.out.println("Hatsfile doesn't exist; not withdrawing...");
            return;
        }

//        g.setColor(JBColor.BLUE);
//        g.drawRect(100, 100, 500, 500);
//
//        g.setColor(JBColor.RED);
//        Point p = editor.visualPositionToXY(editor.getCaretModel().getVisualPosition());
//        g.drawOval(p.x, p.y, 20 - 10, 20 - 10);
//
//        Point p2 = editor.visualPositionToXY(editor.logicalToVisualPosition(new LogicalPosition(9, 1)));
//        g.setColor(JBColor.GREEN);
//        g.drawOval(p2.x, p2.y, 20 - 10, 20 - 10);


        ObjectMapper objectMapper = new ObjectMapper();
        TypeReference<HashMap<String, HashMap<String, ArrayList<CursrlessRange>>>> typeRef = new TypeReference<>() {
        };


        System.out.println("Redrawing...");
        try {
            HashMap<String, HashMap<String, ArrayList<CursrlessRange>>> map = objectMapper.readValue(new File(HATS_PATH), typeRef);

            map.keySet().stream().filter(filePath -> filePath.equals(FileDocumentManager.getInstance().getFile(editor.getDocument()).getPath())).forEach(filePath -> {
                map.get(filePath).keySet().forEach(color -> {
                    map.get(filePath).get(color).forEach(range -> {
                        Point cp = editor.visualPositionToXY(editor.logicalToVisualPosition(new LogicalPosition(range.start.line, range.start.character)));

                        JBColor jColor = JBColor.WHITE;
                        switch (color) {
                            case "red":
                                jColor = JBColor.RED;
                                break;
                            case "pink":
                                jColor = new JBColor(JBColor.getHSBColor((float) (332 / 336.0), (float) .54, (float) .96), JBColor.getHSBColor((float) (332 / 336.0), (float) .54, (float) .96));
                                break;
                            case "yellow":
                                jColor = JBColor.ORANGE;
                                break;
                            case "green":
                                jColor = JBColor.GREEN;
                                break;
                            case "blue":
                                jColor = JBColor.BLUE;
                                break;
                            case "default":
                                jColor = JBColor.GRAY;
                                break;

                        }
                        g.setColor(jColor);
                        int size = 4;
                        g.fillOval(cp.x + 4, cp.y - size / 2 - 0, size, size);
                    });
                });
            });
        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
