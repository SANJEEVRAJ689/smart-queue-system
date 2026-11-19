package com.smartqueue.controller;

import com.smartqueue.dao.ServiceDao;
import com.smartqueue.dao.impl.ServiceDaoImpl;
import com.smartqueue.model.ServiceEntity;
import com.smartqueue.model.Token;
import com.smartqueue.model.TokenStatus;
import com.smartqueue.service.QueueManager;
import com.smartqueue.service.TokenService;
import com.smartqueue.util.AlertUtil;
import com.smartqueue.util.DateTimeUtil;
import com.smartqueue.util.Validator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the Customer Kiosk Token Booking & Status Screen.
 *
 * Features:
 * 1. Service Selection, Customer Info Validation, Priority Options.
 * 2. Real-time Wait Time & Queue Position dynamic preview.
 * 3. Digital Ticket Pass rendering with "Save/Print Slip" option.
 * 4. "Check My Token Status" tab to query live token progress.
 */
public class KioskController implements QueueManager.QueueUpdateListener {
    private static final Logger logger = LoggerFactory.getLogger(KioskController.class);

    // Tab 1: Booking Form Fields
    @FXML private ComboBox<ServiceEntity> cmbServices;
    @FXML private TextField txtCustomerName;
    @FXML private TextField txtCustomerPhone;
    @FXML private CheckBox chkPriority;
    @FXML private ComboBox<String> cmbPriorityReason;
    @FXML private Label lblPreviewAhead;
    @FXML private Label lblPreviewWait;
    @FXML private Button btnBookToken;

    // Generated Ticket Slip View
    @FXML private VBox boxBookingForm;
    @FXML private VBox boxTicketSlip;
    @FXML private Label lblTicketToken;
    @FXML private Label lblTicketService;
    @FXML private Label lblTicketName;
    @FXML private Label lblTicketPosition;
    @FXML private Label lblTicketWait;
    @FXML private Label lblTicketPriority;
    @FXML private Label lblTicketTime;

    // Tab 2: Check Token Status Fields
    @FXML private TextField txtCheckTokenNo;
    @FXML private TextField txtCheckPhone;
    @FXML private VBox boxStatusResult;
    @FXML private Label lblStatusResultToken;
    @FXML private Label lblStatusResultName;
    @FXML private Label lblStatusResultState;
    @FXML private Label lblStatusResultCounter;
    @FXML private Label lblStatusResultWait;
    @FXML private Label lblStatusError;

    private final ServiceDao serviceDao = new ServiceDaoImpl();
    private final TokenService tokenService = new TokenService();
    private Token lastBookedToken;

    @FXML
    public void initialize() {
        loadServices();

        cmbPriorityReason.setItems(FXCollections.observableArrayList(
                "Senior Citizen (60+)",
                "Medical Emergency / Physical Disability",
                "Expecting Mother / Parent with Infant"
        ));
        cmbPriorityReason.getSelectionModel().selectFirst();
        cmbPriorityReason.disableProperty().bind(chkPriority.selectedProperty().not());

        cmbServices.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateLiveEstimate());
        chkPriority.selectedProperty().addListener((obs, oldVal, newVal) -> updateLiveEstimate());

        QueueManager.getInstance().addListener(this);
    }

    private void loadServices() {
        List<ServiceEntity> services = serviceDao.findActiveServices();
        cmbServices.setItems(FXCollections.observableArrayList(services));
        if (!services.isEmpty()) {
            cmbServices.getSelectionModel().selectFirst();
        }
    }

    private void updateLiveEstimate() {
        ServiceEntity selected = cmbServices.getSelectionModel().getSelectedItem();
        if (selected == null) {
            lblPreviewAhead.setText("0");
            lblPreviewWait.setText("0 mins");
            return;
        }

        boolean isPriority = chkPriority.isSelected();
        QueueManager.getInstance().submitWorkerTask(() -> {
            int wait = tokenService.getWaitTimeEstimator().estimateForNewBooking(selected.getId(), isPriority);
            int waitingCount = tokenService.getWaitingTokens(selected.getId()).size();
            int ahead = isPriority ? 0 : waitingCount;

            Platform.runLater(() -> {
                lblPreviewAhead.setText(String.valueOf(ahead));
                lblPreviewWait.setText(wait + " mins");
            });
        });
    }

    @FXML
    public void handleBookToken() {
        ServiceEntity service = cmbServices.getSelectionModel().getSelectedItem();
        if (service == null) {
            AlertUtil.showWarning("Missing Selection", "Please select a department or service desk.");
            return;
        }

        String name = txtCustomerName.getText();
        String phone = txtCustomerPhone.getText();

        if (name == null || name.trim().isEmpty() || name.trim().length() < 2) {
            AlertUtil.showWarning("Required Field", "Please enter customer name (at least 2 letters).");
            return;
        }

        if (!Validator.isValidPhone(phone)) {
            AlertUtil.showWarning("Invalid Phone", "Please enter a valid phone number (at least 7 digits).");
            return;
        }

        boolean isPriority = chkPriority.isSelected();
        String priorityReason = isPriority ? cmbPriorityReason.getSelectionModel().getSelectedItem() : null;

        btnBookToken.setDisable(true);

        QueueManager.getInstance().submitWorkerTask(() -> {
            try {
                Optional<Token> bookedOpt = tokenService.bookToken(
                        service.getId(), name, phone, isPriority, priorityReason
                );

                Platform.runLater(() -> {
                    btnBookToken.setDisable(false);
                    if (bookedOpt.isPresent()) {
                        this.lastBookedToken = bookedOpt.get();
                        showTicketSlip(lastBookedToken);
                    } else {
                        AlertUtil.showError("Booking Failed", "Could not generate token. Please try again.");
                    }
                });
            } catch (Exception e) {
                logger.error("Error booking token", e);
                Platform.runLater(() -> {
                    btnBookToken.setDisable(false);
                    AlertUtil.showError("Error", "Booking error: " + e.getMessage());
                });
            }
        });
    }

    private void showTicketSlip(Token token) {
        lblTicketToken.setText(token.getTokenNumber());
        lblTicketService.setText(token.getServiceName());
        lblTicketName.setText(token.getCustomerName());
        lblTicketPosition.setText(String.valueOf(token.getQueuePosition()));
        lblTicketWait.setText(token.getEstimatedWaitMinutes() + " minutes");
        lblTicketTime.setText(DateTimeUtil.formatDateTime(LocalDateTime.now()));

        if (token.isPriority()) {
            lblTicketPriority.setText("PRIORITY (" + token.getPriorityReason() + ")");
            lblTicketPriority.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
        } else {
            lblTicketPriority.setText("REGULAR");
            lblTicketPriority.setStyle("-fx-text-fill: #0284c7; -fx-font-weight: bold;");
        }

        boxBookingForm.setVisible(false);
        boxBookingForm.setManaged(false);
        boxTicketSlip.setVisible(true);
        boxTicketSlip.setManaged(true);
    }

    @FXML
    public void handlePrintSaveSlip() {
        if (lastBookedToken == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Digital Token Pass");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text File (*.txt)", "*.txt"));
        chooser.setInitialFileName("token_" + lastBookedToken.getTokenNumber() + ".txt");

        File file = chooser.showSaveDialog(lblTicketToken.getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("=========================================\n");
                writer.write("     SMART QUEUE MANAGEMENT SYSTEM       \n");
                writer.write("           DIGITAL TOKEN PASS            \n");
                writer.write("=========================================\n\n");
                writer.write("Token Number     : " + lastBookedToken.getTokenNumber() + "\n");
                writer.write("Department       : " + lastBookedToken.getServiceName() + "\n");
                writer.write("Customer Name    : " + lastBookedToken.getCustomerName() + "\n");
                writer.write("Phone Number     : " + lastBookedToken.getCustomerPhone() + "\n");
                writer.write("Priority Status  : " + (lastBookedToken.isPriority() ? "PRIORITY" : "REGULAR") + "\n");
                writer.write("Queue Position   : " + lastBookedToken.getQueuePosition() + "\n");
                writer.write("Estimated Wait   : " + lastBookedToken.getEstimatedWaitMinutes() + " mins\n");
                writer.write("Issued At        : " + DateTimeUtil.formatDateTime(LocalDateTime.now()) + "\n\n");
                writer.write("-----------------------------------------\n");
                writer.write("Please watch the Live Display Board in   \n");
                writer.write("the Waiting Hall for your token announcement.\n");
                writer.write("=========================================\n");
                AlertUtil.showInfo("Saved", "Token slip saved successfully to:\n" + file.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to save token slip", e);
                AlertUtil.showError("Save Failed", "Could not write token slip file: " + e.getMessage());
            }
        }
    }

    @FXML
    public void handleBookAnother() {
        txtCustomerName.clear();
        txtCustomerPhone.clear();
        chkPriority.setSelected(false);

        boxTicketSlip.setVisible(false);
        boxTicketSlip.setManaged(false);
        boxBookingForm.setVisible(true);
        boxBookingForm.setManaged(true);

        updateLiveEstimate();
    }

    @FXML
    public void handleCheckStatus() {
        String tokenNo = txtCheckTokenNo.getText();
        String phone = txtCheckPhone.getText();

        if (tokenNo == null || tokenNo.trim().isEmpty() || phone == null || phone.trim().isEmpty()) {
            lblStatusError.setText("Please enter both Token Number and registered Phone Number.");
            lblStatusError.setVisible(true);
            return;
        }

        lblStatusError.setText("");
        lblStatusError.setVisible(false);

        QueueManager.getInstance().submitWorkerTask(() -> {
            Optional<Token> tokenOpt = tokenService.checkTokenStatus(tokenNo.trim().toUpperCase(), phone.trim(), LocalDate.now());
            Platform.runLater(() -> {
                if (tokenOpt.isPresent()) {
                    Token t = tokenOpt.get();
                    lblStatusResultToken.setText(t.getTokenNumber());
                    lblStatusResultName.setText(t.getCustomerName());
                    lblStatusResultState.setText(t.getStatus().name());

                    if (t.getStatus() == TokenStatus.WAITING) {
                        lblStatusResultState.setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold;");
                        int ahead = tokenService.getWaitTimeEstimator().estimateForToken(t.getId(), t.getServiceId());
                        lblStatusResultCounter.setText("In Queue");
                        lblStatusResultWait.setText(ahead + " mins remaining");
                    } else if (t.getStatus() == TokenStatus.CALLED || t.getStatus() == TokenStatus.SERVING) {
                        lblStatusResultState.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                        lblStatusResultCounter.setText("Counter " + (t.getCounterNumber() != null ? t.getCounterNumber() : "Assigned"));
                        lblStatusResultWait.setText("NOW IN ATTENDANCE");
                    } else {
                        lblStatusResultState.setStyle("-fx-text-fill: #64748b; -fx-font-weight: bold;");
                        lblStatusResultCounter.setText("--");
                        lblStatusResultWait.setText("Completed / Finished");
                    }

                    boxStatusResult.setVisible(true);
                    boxStatusResult.setManaged(true);
                } else {
                    boxStatusResult.setVisible(false);
                    boxStatusResult.setManaged(false);
                    lblStatusError.setText("No active token found matching '" + tokenNo.trim() + "' and this phone number today.");
                    lblStatusError.setVisible(true);
                }
            });
        });
    }

    @Override
    public void onQueueUpdated() {
        updateLiveEstimate();
    }
}
