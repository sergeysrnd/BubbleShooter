module com.kilocade.bubbleshooter {
   requires javafx.controls;
   requires transitive javafx.graphics;
   requires javafx.base;

   exports com.kilocade.bubbleshooter;
   opens com.kilocade.bubbleshooter to javafx.graphics;
}