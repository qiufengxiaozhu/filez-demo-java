package com.filez.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 水印配置
 * 示例：
 * line1: “李明 liming@lenovo.com”; // 比如 文件最后更新者信息
 * line2: “张三 zhangshan@lenovo.com”; // 比如：文件编辑者/预览者信息
 * line3: “”; //比如：其他自定义文字
 * line4: “”; //比如：其他自定义文字
 * withDate: true; // 每行文字后，是否带日期
 * fontcolor: “#FD4147”; //RGB 值，
 * transparent: 30; //0 - 100，透明度。值越小，透明效果越明显。
 * rotation: 315; // 旋转角度 0 - 360, 左倾斜=315，右倾斜=45, 水平=0
 * fontsize: “72”;
 * font: “黑体”;
 * spacing: 50; //行间距，列间距均为 50 像素
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocWaterMark {

    @Builder.Default
    private String line1 = "";

    private String line2;

    private String line3;

    private String line4;

    private boolean withDate;

    private String fontcolor;

    private int transparent;

    private int spacing;

    private int rotation;

    private String fontsize;

    @Builder.Default
    private String font = "黑体";
}
