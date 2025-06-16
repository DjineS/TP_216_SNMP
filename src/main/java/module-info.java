module com.example.tp_216_snmp {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires org.snmp4j;

    exports com.example.tp_216_snmp.frontend;
    opens com.example.tp_216_snmp.frontend to javafx.fxml;
}