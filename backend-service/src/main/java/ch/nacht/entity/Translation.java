package ch.nacht.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "translation")
public class Translation {

    @Id
    @NotBlank(message = "key is required")
    @Size(max = 200, message = "key must not exceed 200 characters")
    private String key;

    @Column(columnDefinition = "TEXT")
    @Size(max = 10000, message = "deutsch must not exceed 10000 characters")
    private String deutsch;

    @Column(columnDefinition = "TEXT")
    @Size(max = 10000, message = "englisch must not exceed 10000 characters")
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
