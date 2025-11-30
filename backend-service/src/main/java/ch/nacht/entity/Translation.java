package ch.nacht.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "translation")
public class Translation {

    @Id
    private String key;

    @Column(columnDefinition = "TEXT")
    private String deutsch;

    @Column(columnDefinition = "TEXT")
    private String englisch;

    public Translation() {
    }

    public Translation(String key, String deutsch, String englisch) {
        this.key = key;
        this.deutsch = deutsch;
        this.englisch = englisch;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDeutsch() {
        return deutsch;
    }

    public void setDeutsch(String deutsch) {
        this.deutsch = deutsch;
    }

    public String getEnglisch() {
        return englisch;
    }

    public void setEnglisch(String englisch) {
        this.englisch = englisch;
    }
}
