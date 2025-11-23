package ch.nacht.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "einheit", schema = "zev")
public class Einheit {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "einheit_seq")
    @SequenceGenerator(name = "einheit_seq", sequenceName = "zev.einheit_seq", allocationSize = 1)
    private Long id;

    @Column(name = "name", length = 30, nullable = false)
    private String name;

    public Einheit() {
    }

    public Einheit(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Einheit{id=" + id + ", name='" + name + "'}";
    }
}
