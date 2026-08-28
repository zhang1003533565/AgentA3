package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "paper_layout", uniqueConstraints = {
        @UniqueConstraint(name = "uk_paper_layout_paper_id", columnNames = "paper_id")
})
public class PaperLayout {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_id", insertable = false, updatable = false)
    private Long paperId;

    @Column(name = "template_name", nullable = false, length = 32)
    private String templateName;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paper_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_paper_layout_paper"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Paper paper;

    @Column(name = "paper_size", nullable = false, length = 10)
    private String paperSize;

    @Column(nullable = false, length = 16)
    private String orientation;

    @Column(name = "columns_count", nullable = false)
    private Integer columnsCount;

    @Column(name = "column_gap", nullable = false, precision = 6, scale = 2)
    private BigDecimal columnGap;

    @Column(name = "binding_line", nullable = false)
    private Boolean bindingLine;

    @Column(name = "binding_position", nullable = false, length = 10)
    private String bindingPosition;

    @Column(name = "margin_top", nullable = false, precision = 6, scale = 2)
    private BigDecimal marginTop;

    @Column(name = "margin_bottom", nullable = false, precision = 6, scale = 2)
    private BigDecimal marginBottom;

    @Column(name = "margin_left", nullable = false, precision = 6, scale = 2)
    private BigDecimal marginLeft;

    @Column(name = "margin_right", nullable = false, precision = 6, scale = 2)
    private BigDecimal marginRight;

    @Column(name = "show_school", nullable = false)
    private Boolean showSchool;

    @Column(name = "show_grade", nullable = false)
    private Boolean showGrade;

    @Column(name = "show_class", nullable = false)
    private Boolean showClass;

    @Column(name = "show_name", nullable = false)
    private Boolean showName;

    @Column(name = "show_student_no", nullable = false)
    private Boolean showStudentNo;

    @Column(name = "show_student_info", nullable = false)
    private Boolean showStudentInfo;

    @Column(name = "student_fields", nullable = false, length = 255)
    private String studentFields;

    @Column(name = "margin_preset", nullable = false, length = 32)
    private String marginPreset;

    @Column(name = "title_font_size", nullable = false)
    private Integer titleFontSize;

    @Column(name = "subtitle_font_size", nullable = false)
    private Integer subtitleFontSize;

    @Column(name = "body_font_size", nullable = false)
    private Integer bodyFontSize;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    void create() {
        createTime = LocalDateTime.now();
        updateTime = createTime;
    }

    @PreUpdate
    void update() {
        updateTime = LocalDateTime.now();
    }
}
