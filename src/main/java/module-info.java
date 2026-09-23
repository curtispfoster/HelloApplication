module com.example.helloapplication {
    requires javafx.controls;
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires java.logging;
    requires org.junit.jupiter.api;
    requires org.bouncycastle.provider;

    exports com.example.helloapplication;
    opens com.example.helloapplication to org.junit.platform.commons;
}