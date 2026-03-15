module com.kilocade.bubbleshooter {
   requires javafx.controls;
   requires transitive javafx.graphics;
   requires javafx.base;
   requires java.desktop;

   exports com.kilocade.bubbleshooter;
   opens com.kilocade.bubbleshooter to javafx.graphics;
}
