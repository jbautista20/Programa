package org.hamming;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

public class MainApp extends Application {

    private byte[] originalFileBytes;
    private File activeFile; // Ultimo archivo cargado o generado (para operaciones de cambio)

    private TextFlow leftTextFlow = new TextFlow();
    private TextFlow rightTextFlow = new TextFlow();
    private Label statusLabel = new Label("Listo.");

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("PM-Hamming TIyC UNSL 2026 - MontenegroPino");

        // toolbar
        Button btnLoad = new Button(" Cargar Archivo ");
        btnLoad.setOnAction(e -> loadFile(primaryStage));

        ComboBox<String> blockSizeBox = new ComboBox<>();
        blockSizeBox.getItems().addAll("8 bits", "1024 bits", "16384 bits");
        blockSizeBox.setValue("8 bits");

        Button btnProtect = new Button(" Proteger ");
        btnProtect.setOnAction(e -> protectFile(primaryStage, blockSizeBox.getValue()));

        Button btnErrors = new Button(" Introducir Errores ");
        btnErrors.setOnAction(e -> introduceErrors(primaryStage));

        Button btnDecodeRaw = new Button(" Desproteger SIN Corregir ");
        btnDecodeRaw.setOnAction(e -> unprotect(primaryStage, false));

        Button btnDecodeCorrect = new Button(" Desproteger CORRIGIENDO ");
        btnDecodeCorrect.setOnAction(e -> unprotect(primaryStage, true));

        Button btnEncrypt = new Button(" Encriptar XOR (Time-Lock) ");
        btnEncrypt.setOnAction(e -> encryptFile(primaryStage));

        HBox topBar = new HBox(10, btnLoad, new Label("Bloque:"), blockSizeBox, btnProtect, btnErrors, btnDecodeRaw, btnDecodeCorrect, btnEncrypt);
        topBar.setPadding(new Insets(10));
        topBar.setAlignment(Pos.CENTER_LEFT);

        // centrado de texto
        ScrollPane leftScroll = new ScrollPane(leftTextFlow);
        leftScroll.setFitToWidth(true);
        VBox leftBox = new VBox(new Label("Archivo Original / Referencia"), leftScroll);
        VBox.setVgrow(leftScroll, Priority.ALWAYS);

        ScrollPane rightScroll = new ScrollPane(rightTextFlow);
        rightScroll.setFitToWidth(true);
        VBox rightBox = new VBox(new Label("Archivo Procesado / Visualización"), rightScroll);
        VBox.setVgrow(rightScroll, Priority.ALWAYS);

        // scrolling sincronizado
        leftScroll.vvalueProperty().bindBidirectional(rightScroll.vvalueProperty());

        SplitPane splitPane = new SplitPane(leftBox, rightBox);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        VBox root = new VBox(topBar, splitPane, statusLabel);
        root.setPadding(new Insets(10));
        statusLabel.setPadding(new Insets(5));
        statusLabel.setTextFill(Color.NAVY);

        Scene scene = new Scene(root, 1000, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void loadFile(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivos TXT", "*.txt"));
        File f = fc.showOpenDialog(stage);
        if (f != null) {
            try {
                originalFileBytes = Files.readAllBytes(f.toPath());
                activeFile = f;
                updatePanel(leftTextFlow, new String(originalFileBytes), null);
                rightTextFlow.getChildren().clear();
                showStatus("Archivo " + f.getName() + " cargado exitosamente. (" + originalFileBytes.length + " bytes)");
            } catch (Exception ex) {
                showError("No se pudo cargar el archivo: " + ex.getMessage());
            }
        }
    }

    private int extractN(String choice) {
        if (choice.startsWith("8")) return 8;
        if (choice.startsWith("1024")) return 1024;
        if (choice.startsWith("16384")) return 16384;
        return 8;
    }

    private String getExt(int N) {
        if (N == 8) return ".HA1";
        if (N == 1024) return ".HA2";
        if (N == 16384) return ".HA3";
        return ".U";
    }
    private String getErrExt(int N) {
        if (N == 8) return ".HE1";
        if (N == 1024) return ".HE2";
        if (N == 16384) return ".HE3";
        return ".U";
    }

    private void protectFile(Stage stage, String choice) {
        if (originalFileBytes == null) {
            showError("Debe cargar un archivo TXT primero.");
            return;
        }
        try {
            int N = extractN(choice);
            byte[] protectedBytes = HammingCodec.protect(originalFileBytes, N);
            String ext = getExt(N);
            
            File outFile = new File(activeFile.getParent(), activeFile.getName().replace(".txt", "") + ext);
            Files.write(outFile.toPath(), protectedBytes);
            activeFile = outFile;
            
            showStatus("Archivo protegido creado: " + outFile.getName() + " (" + protectedBytes.length + " bytes)");
        } catch (Exception ex) {
            showError("Error al proteger: " + ex.getMessage());
        }
    }

    private void introduceErrors(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccione Archivo .HAx protegido");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Hamming Protegido", "*.HA1", "*.HA2", "*.HA3"));
        File f = fc.showOpenDialog(stage);
        if (f != null) {
            try {
                byte[] protectedBytes = Files.readAllBytes(f.toPath());
                int N = 8;
                if (f.getName().endsWith(".HA2")) N = 1024;
                if (f.getName().endsWith(".HA3")) N = 16384;

                byte[] errorBytes = HammingCodec.introduceErrors(protectedBytes, N);
                String ext = getErrExt(N);
                
                File outFile = new File(f.getParent(), f.getName().replace(".HA1", "").replace(".HA2", "").replace(".HA3", "") + ext);
                Files.write(outFile.toPath(), errorBytes);
                activeFile = outFile;
                
                showStatus("Archivo con errores generado: " + outFile.getName());
            } catch (Exception ex) {
                showError("No se pudo corromper: " + ex.getMessage());
            }
        }
    }

    private void unprotect(Stage stage, boolean correctErrors) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccione Archivo .HAx o .HEx");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Hamming Protegido", "*.HA1", "*.HA2", "*.HA3","*.HE1","*.HE2","*.HE3"));
        File f = fc.showOpenDialog(stage);
        if (f != null) {
            try {
                byte[] bytes = Files.readAllBytes(f.toPath());
                int N = 8;
                if (f.getName().contains("2")) N = 1024;
                if (f.getName().contains("3")) N = 16384;

                byte[] resultBytes = HammingCodec.unprotect(bytes, N, correctErrors);
                
                String extOriginal = correctErrors ? ".DC" : ".DE";
                String numeral = f.getName().substring(f.getName().length()-1);
                File outFile = new File(f.getParent(), f.getName().substring(0, f.getName().lastIndexOf(".")) + extOriginal + numeral);
                Files.write(outFile.toPath(), resultBytes);

                String rawText = new String(resultBytes);
                if (!correctErrors && originalFileBytes != null) {
                    updatePanel(rightTextFlow, rawText, new String(originalFileBytes));
                } else {
                    updatePanel(rightTextFlow, rawText, null);
                }

                showStatus("Archivo desprotegido generado: " + outFile.getName() + " | Recuperados " + resultBytes.length + " bytes.");
            } catch (Exception ex) {
                showError("Error al desproteger: " + ex.getMessage());
            }
        }
    }

    private void encryptFile(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccione Archivo para Encriptar");
        File f = fc.showOpenDialog(stage);
        if (f != null) {
            TextInputDialog dialog = new TextInputDialog("1");
            dialog.setTitle("Time-Lock XOR Encryption");
            dialog.setHeaderText("Introduce los minutos a futuro para bloquear la apertura");
            dialog.setContentText("Minutos:");
            dialog.showAndWait().ifPresent(mins -> {
                try {
                    int minutes = Integer.parseInt(mins);
                    LocalDateTime targetTime = LocalDateTime.now().plusMinutes(minutes);
                    
                    byte[] data = Files.readAllBytes(f.toPath());
                    // SI ES ABRIR
                    if (f.getName().endsWith(".enc")) {
                          byte[] decrypted = CryptoTimeHelper.unpackTimeLock(data);
                          File outFile = new File(f.getParent(), f.getName().replace(".enc", "_dec.txt"));
                          Files.write(outFile.toPath(), decrypted);
                          showStatus("Desencriptado exitosamente. Archivo: " + outFile.getName());
                    } else {
                          byte[] encrypted = CryptoTimeHelper.packWithTimeLock(data, targetTime);
                          File outFile = new File(f.getParent(), f.getName() + ".enc");
                          Files.write(outFile.toPath(), encrypted);
                          showStatus("Encriptado con XOR! No se podrá abrir hasta: " + targetTime.toString());
                    }
                } catch (Exception ex) {
                    showError("Error encriptación: " + ex.getMessage());
                }
            });
        }
    }

    private void updatePanel(TextFlow panel, String textToShow, String comparison) {
        panel.getChildren().clear();
        if (comparison == null || comparison.isEmpty()) {
            Text text = new Text(textToShow);
            text.setFont(Font.font("Monospaced", 14));
            panel.getChildren().add(text);
        } else {
            // Comparacion caracter a caracter para mostrar rojizo
            for (int i = 0; i < textToShow.length(); i++) {
                char curr = textToShow.charAt(i);
                char ref = (i < comparison.length()) ? comparison.charAt(i) : 0;
                Text t = new Text(String.valueOf(curr));
                t.setFont(Font.font("Monospaced", 14));
                if (curr != ref) {
                    t.setFill(Color.RED);
                    t.setStyle("-fx-font-weight: bold;");
                }
                panel.getChildren().add(t);
            }
        }
    }

    private void showStatus(String msg) {
        statusLabel.setTextFill(Color.GREEN);
        statusLabel.setText(msg);
    }
    private void showError(String msg) {
        statusLabel.setTextFill(Color.RED);
        statusLabel.setText(msg);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
