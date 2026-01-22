# Umsetzungsplan: Tarifverwaltung - Tarif kopieren

## Zusammenfassung

Erweiterung der bestehenden Tarifverwaltung um eine "Kopieren"-Funktion im Kebab-Menü. Beim Kopieren wird das Tarif-Formular mit den Werten des ausgewählten Tarifs vorausgefüllt geöffnet (ohne ID), sodass der Benutzer einen neuen Tarif basierend auf einem bestehenden erstellen kann.

---

## Betroffene Komponenten

| Typ | Datei | Änderungsart |
|-----|-------|--------------|
| Frontend Component | `frontend-service/src/app/components/tarif-list/tarif-list.component.ts` | Änderung |
| Frontend Icons | `frontend-service/src/app/components/icon/icons.ts` | Änderung |
| DB Migration | `backend-service/src/main/resources/db/migration/V35__Add_Kopieren_Translation.sql` | Neu |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Icon hinzufügen | "copy" Icon zur Icon-Registry hinzufügen |
| [x] | 2. Kebab-Menü erweitern | Menüpunkt "Kopieren" zwischen "Bearbeiten" und "Löschen" einfügen |
| [x] | 3. Kopier-Logik implementieren | `onCopy()` Methode implementieren, die Tarif ohne ID ins Formular lädt |
| [x] | 4. Übersetzung hinzufügen | Flyway-Migration für Translation-Key "KOPIEREN" |

---

## Detailbeschreibung der Phasen

### Phase 1: Icon hinzufügen

**Datei:** `frontend-service/src/app/components/icon/icons.ts`

Feather Icon "copy" hinzufügen:

```typescript
'copy': '<rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>',
```

### Phase 2: Kebab-Menü erweitern

**Datei:** `frontend-service/src/app/components/tarif-list/tarif-list.component.ts`

Menüpunkte anpassen (Zeile 31-34):

```typescript
menuItems: KebabMenuItem[] = [
  { label: 'BEARBEITEN', action: 'edit', icon: 'edit-2' },
  { label: 'KOPIEREN', action: 'copy', icon: 'copy' },
  { label: 'LOESCHEN', action: 'delete', danger: true, icon: 'trash-2' }
];
```

### Phase 3: Kopier-Logik implementieren

**Datei:** `frontend-service/src/app/components/tarif-list/tarif-list.component.ts`

Neue Methode `onCopy()` hinzufügen:

```typescript
onCopy(tarif: Tarif): void {
  // Kopie ohne ID erstellen - wird als neuer Tarif behandelt
  const { id, ...tarifOhneId } = tarif;
  this.selectedTarif = { ...tarifOhneId } as Tarif;
  this.showForm = true;
}
```

Switch-Case in `onMenuAction()` erweitern (Zeile 82-91):

```typescript
onMenuAction(action: string, tarif: Tarif): void {
  switch (action) {
    case 'edit':
      this.onEdit(tarif);
      break;
    case 'copy':
      this.onCopy(tarif);
      break;
    case 'delete':
      this.onDelete(tarif.id);
      break;
  }
}
```

### Phase 4: Übersetzung hinzufügen

**Datei:** `backend-service/src/main/resources/db/migration/V[XX]__Add_Kopieren_Translation.sql`

```sql
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('KOPIEREN', 'Kopieren', 'Copy')
ON CONFLICT (key) DO NOTHING;
```

---

## Validierungen

### Frontend-Validierungen
- Keine zusätzlichen Validierungen erforderlich
- Bestehende Formular-Validierungen greifen beim kopierten Tarif

### Backend-Validierungen
- Keine zusätzlichen Validierungen erforderlich
- Bestehende Überlappungsprüfung greift beim Speichern des kopierten Tarifs

---

## Offene Punkte / Annahmen

1. **Annahme:** Beim Kopieren werden alle Felder übernommen ausser der ID
2. **Annahme:** Die Gültigkeitsdaten werden 1:1 übernommen - der Benutzer muss diese ggf. anpassen, um Überlappungskonflikte zu vermeiden
3. **Annahme:** Die Übersetzung "KOPIEREN" existiert noch nicht in der Datenbank
4. **Annahme:** Das Formular zeigt beim kopierten Tarif den Titel "Neuer Tarif" (da keine ID vorhanden)
