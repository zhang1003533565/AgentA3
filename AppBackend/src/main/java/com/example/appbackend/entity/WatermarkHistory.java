package com.example.appbackend.entity;

import jakarta.persistence.*; 

@Entity 
@Table(name = "history_record") 
public class WatermarkHistory {
    
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY) 
    private Long id;
    
    private String title;
    private String time;
    private String format;
    private String editPage;
    
    // 【核心修复】：改成 @Lob，告诉数据库这是一个“大文本对象”
    // 无论你传多长的图片链接，它都能存进去，绝对不会报“太长”的错误！
    @Lob 
    private String imgUrl;

    public WatermarkHistory() {}

    public WatermarkHistory(String title, String time, String format, String editPage, String imgUrl) {
        this.title = title;
        this.time = time;
        this.format = format;
        this.editPage = editPage;
        this.imgUrl = imgUrl;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getEditPage() { return editPage; }
    public void setEditPage(String editPage) { this.editPage = editPage; }
    public String getImgUrl() { return imgUrl; }
    public void setImgUrl(String imgUrl) { this.imgUrl = imgUrl; }
}