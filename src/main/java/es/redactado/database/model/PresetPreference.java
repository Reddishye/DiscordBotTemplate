package es.redactado.database.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A guild or user preset choice that survives a restart. */
@Entity
@Table(
        name = "preset_preference",
        uniqueConstraints = @UniqueConstraint(columnNames = {"scope", "subject_id"}))
public class PresetPreference extends BaseDomain {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PreferenceScope scope;

    @Column(name = "subject_id", nullable = false)
    private long subjectId;

    @Column(name = "preset_name", nullable = false, length = 64)
    private String presetName;

    protected PresetPreference() {}

    public PresetPreference(PreferenceScope scope, long subjectId, String presetName) {
        this.scope = scope;
        this.subjectId = subjectId;
        this.presetName = presetName;
    }

    public PreferenceScope getScope() {
        return scope;
    }

    public long getSubjectId() {
        return subjectId;
    }

    public String getPresetName() {
        return presetName;
    }

    public void setPresetName(String presetName) {
        this.presetName = presetName;
    }
}
