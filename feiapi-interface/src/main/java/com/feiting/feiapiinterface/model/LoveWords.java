package com.feiting.feiapiinterface.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Data;

/**
 * 
 * @TableName love_words
 */
@TableName(value ="love_words")
@Data
public class LoveWords implements Serializable {
    /**
     * 主键 id
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 土味情话
     */
    private String words;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}