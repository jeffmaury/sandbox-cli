package me.jeffmaury.sandboxcli;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

import org.keycloak.OAuthErrorException;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.ServerRequest;
import org.keycloak.adapters.installed.KeycloakInstalled;
import org.keycloak.common.VerificationException;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.adapters.config.AdapterConfig;


public class SandboxCLI extends Application {

    private VBox root;
    private Label label;
    private AccessTokenResponse response;
    private SandboxProcessor processor;
    private String countryCode = "";
    private String phoneNumber = "";
    private String verificationCode = "";

    private void setMessage(String message) {
        Platform.runLater(() -> label.setText(message));
    }

    @Override
    public void start(Stage stage) throws IOException {
        root = new VBox(30);
        Scene scene = new Scene(root, -1, -1);
        stage.setTitle("Login to Red Hat Developer Sandbox");
        stage.setScene(scene);
        stage.show();

        label = new Label();
        WebView view = new WebView();
        root.getChildren().addAll(label, view);

        loginToSSO(view);
    }

    private void loginToSSO(WebView view) {
        AdapterConfig config = new AdapterConfig();
        config.setAuthServerUrl("https://sso.redhat.com/auth");
        config.setRealm("redhat-external");
        config.setResource("vscode-redhat-account");
        config.setPkce(true);
        config.setPublicClient(true);
        KeycloakDeployment deployment = KeycloakDeploymentBuilder.build(config);
        KeycloakInstalled installed = new KeycloakInstalled(deployment);
        installed.setDesktopProvider(new KeycloakInstalled.DesktopProvider() {
            @Override
            public void browse(URI uri) throws IOException {
                Platform.runLater(() -> view.getEngine().load(uri.toString()));
            }
        });
        CompletableFuture.runAsync(() -> {
            try {
                installed.loginDesktop();
                response = installed.getTokenResponse();
                Platform.runLater(() -> {
                    root.getChildren().removeAll(view);
                    setMessage("Login to Red Hat SSO successful!");
                    startSandboxWorkflow();
                });
            } catch (IOException | VerificationException | OAuthErrorException | URISyntaxException | ServerRequest.HttpFailure | InterruptedException e) {
                setMessage("An error occurred while logging in to Red Hat SSO: " + e.getMessage());
            }
        });
    }

    private void startSandboxWorkflow() {
        if (processor == null) {
            processor = new SandboxProcessor(response.getIdToken());
        }
        boolean stop = false;
        try {
            while (!stop) {
                SandboxProcessor.State state = processor.advance(countryCode, phoneNumber, verificationCode);
                stop = state.isNeedsInteraction();
                if (state == SandboxProcessor.State.NEEDS_VERIFICATION) {
                    createVerificationUI();
                } else if (state == SandboxProcessor.State.CONFIRM_VERIFICATION) {
                    createConfirmationUI();
                }
            }
        } catch (IOException e) {
            setMessage("An error occurred while processing the sandbox workflow: " + e.getMessage());
        }
    }

    private void createVerificationUI() {
        setMessage("Your Red Hat Developer Sandbox needs to be verified, enter your country code and phone number and click 'Verify'");
        Label countryCodeLabel = new Label("Country code");
        Label phoneNumberLabel = new Label("Phone number");
        TextField countryCodeField = new TextField();
        TextField phoneNumberField = new TextField();
        Button verifyButton = new Button ("Verify");
        GridPane gridPane = new GridPane();
        gridPane.addRow(0, countryCodeLabel,countryCodeField );
        gridPane.addRow(1, phoneNumberLabel,phoneNumberField );
        gridPane.addRow(2, verifyButton);
        root.getChildren().add(gridPane);
        verifyButton.setOnAction(event -> {
            countryCode = countryCodeField.getText();
            phoneNumber = phoneNumberField.getText();
            root.getChildren().removeAll(gridPane);
            startSandboxWorkflow();
        });
    }

    private void createConfirmationUI() {
        setMessage("You need to send the verification code received on your phone, enter the verification code and phone number and click 'Verify'");
        Label countryCodeLabel = new Label("Verification code");
        TextField countryCodeField = new TextField();
        Button verifyButton = new Button ("Verify");
        GridPane gridPane = new GridPane();
        gridPane.addRow(0, countryCodeLabel,countryCodeField );
        gridPane.addRow(1, verifyButton);
        root.getChildren().add(gridPane);
        verifyButton.setOnAction(event -> {
            countryCode = countryCodeField.getText();
            root.getChildren().removeAll(gridPane);
            startSandboxWorkflow();
        });
    }

    public static void main(String[] args) {
        launch();
    }
}