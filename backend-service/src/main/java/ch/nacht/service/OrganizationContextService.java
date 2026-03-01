package ch.nacht.service;

import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

import java.util.ArrayList;
import java.util.List;

/**
 * Service für den Organisationskontext des aktuellen Requests.
 * Speichert die aktuelle org_id und die Liste aller verfügbaren Organisationen des Benutzers.
 */
@Service
@RequestScope
public class OrganizationContextService {

    private Long currentOrgId;
    private List<Long> availableOrgIds = new ArrayList<>();
    private String currentOrgName;

    /**
     * Liefert die aktuelle Organisations-ID.
     */
    public Long getCurrentOrgId() {
        return currentOrgId;
    }

    /**
     * Setzt die aktuelle Organisations-ID.
     */
    public void setCurrentOrgId(Long orgId) {
        this.currentOrgId = orgId;
    }

    /**
     * Liefert alle verfügbaren Organisations-IDs des Benutzers.
     */
    public List<Long> getAvailableOrgIds() {
        return availableOrgIds;
    }

    /**
     * Setzt die verfügbaren Organisations-IDs.
     */
    public void setAvailableOrgIds(List<Long> orgIds) {
        this.availableOrgIds = orgIds != null ? orgIds : new ArrayList<>();
    }

    /**
     * Liefert den Namen der aktuellen Organisation.
     */
    public String getCurrentOrgName() {
        return currentOrgName;
    }

    /**
     * Setzt den Namen der aktuellen Organisation.
     */
    public void setCurrentOrgName(String orgName) {
        this.currentOrgName = orgName;
    }

    /**
     * Prüft, ob der Benutzer mehreren Organisationen angehört.
     */
    public boolean hasMultipleOrganizations() {
        return availableOrgIds.size() > 1;
    }

    /**
     * Prüft, ob eine Organisation gesetzt ist.
     */
    public boolean hasOrganization() {
        return currentOrgId != null;
    }
}
