package com.github.phillco.talonjetbrains.cursorless

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.ui.JBColor
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.awt.Graphics
import java.io.File
import java.util.function.Consumer
import javax.swing.JComponent

class CursorlessContainer(private val editor: Editor) : JComponent() {
    private val parent: JComponent

    init {
        this.parent = editor.contentComponent
        this.parent.add(this)
        this.bounds = parent.bounds
        isVisible = true
        println("cursorless container initialized!")
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        if (!File(System.getProperty("user.home") + "/.enable-cursorless-jetbrains").exists()) {
            println("~/.enable-cursorless-jetbrains-old doesn't exist; not withdrawing...")
            return
        }

        if (!File(HATS_PATH).exists()) {
            println("Hatsfile doesn't exist; not withdrawing...")
            return
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
//        val objectMapper = ObjectMapper()
//        val typeRef: TypeReference<HashMap<String, HashMap<String, ArrayList<CursorlessRange>>>> =
//            object :
//                TypeReference<HashMap<String?, HashMap<String?, ArrayList<CursorlessRange?>?>?>?>() {}

//        val mapper = jacksonObjectMapper()
//        mapper.registerKotlinModule()

        val format = Json { isLenient = true }

//        val hats = Json.
//            File(
//                HATS_PATH)
//        )

        val map =
            format.decodeFromString<HashMap<String, HashMap<String, ArrayList<CursorlessRange>>>>(
                File(HATS_PATH).readText()
            )

        println("Redrawing...")
        map.keys.stream().filter { filePath: String ->
            filePath == FileDocumentManager.getInstance().getFile(
                editor.document
            )!!.path
        }.forEach { filePath: String ->
            map[filePath]!!.keys.forEach(Consumer { color: String ->
                map[filePath]!![color]!!.forEach(Consumer { range: CursorlessRange ->
                    val cp = editor.visualPositionToXY(
                        editor.logicalToVisualPosition(
                            LogicalPosition(
                                range.start!!.line,
                                range.start!!.character
                            )
                        )
                    )
                    var jColor = JBColor.WHITE
                    when (color) {
                        "red" -> jColor = JBColor.RED
                        "pink" -> jColor = JBColor(
                            JBColor.getHSBColor(
                                (332 / 336.0).toFloat(),
                                .54.toFloat(),
                                .96.toFloat()
                            ), JBColor.getHSBColor(
                                (332 / 336.0).toFloat(),
                                .54.toFloat(),
                                .96.toFloat()
                            )
                        )
                        "yellow" -> jColor = JBColor.ORANGE
                        "green" -> jColor = JBColor.GREEN
                        "blue" -> jColor = JBColor.BLUE
                        "default" -> jColor = JBColor.GRAY
                    }
                    g.color = jColor
                    val size = 4
                    g.fillOval(
                        cp.x + 4, cp.y - size / 2 - 0, size, size
                    )
                })
            })
        }
    }

    companion object {
        var HATS_PATH = System.getProperty("user.home") + "/.vscode-hats.json"
    }
}