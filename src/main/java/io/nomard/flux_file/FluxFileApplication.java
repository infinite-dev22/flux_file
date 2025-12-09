package io.nomard.flux_file;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import fr.brouillard.oss.cssfx.CSSFX;
import io.nomard.flux_file.presentation.view.main.FileManagerView;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class FluxFileApplication extends Application {

    private ConfigurableApplicationContext springContext;

    private static void applyFadeTransition(Parent root) {
        FadeTransition fadeIn = new FadeTransition(Duration.seconds(1), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private static void applySystemTheme(Scene scene) {
        Platform.Preferences preferences = Platform.getPreferences();
        if (preferences != null) {
            ColorScheme colorScheme = preferences.getColorScheme();
            updateTheme(colorScheme, scene);
            preferences.colorSchemeProperty().addListener(
                    (_, _, newValue) -> updateTheme(newValue, scene)
            );
        }
    }

    private static void updateTheme(ColorScheme colorScheme, Scene scene) {
        if (colorScheme == ColorScheme.DARK) {
            Application.setUserAgentStylesheet(new CupertinoDark().getUserAgentStylesheet());
        } else {
            Application.setUserAgentStylesheet(new CupertinoLight().getUserAgentStylesheet());
        }
    }

    @Override
    public void init() {
        springContext = SpringApplication.run(Main.class);
    }

    @Override
    public void start(Stage primaryStage) {
        CSSFX.start();
        FileManagerView view = springContext.getBean(FileManagerView.class);

        Scene scene = new Scene(view.getView(), 1200, 700);
        applySystemTheme(scene);
        applyFadeTransition(scene.getRoot());

        primaryStage.setTitle("Reactive File Manager");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        springContext.close();
        Platform.exit();
    }
}
