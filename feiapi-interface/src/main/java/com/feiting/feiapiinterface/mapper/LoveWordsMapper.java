package com.feiting.feiapiinterface.mapper;

import com.feiting.feiapiinterface.model.LoveWords;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
* @author asus
* @description 针对表【love_words】的数据库操作Mapper
* @createDate 2023-03-14 18:02:28
* @Entity com.feiting.feiapiinterface.model.LoveWords
*/
public interface LoveWordsMapper extends BaseMapper<LoveWords> {

    /**
     * 随机获取一条土味情话
     *
     * @return 随机土味情话，表为空时返回 null
     */
    LoveWords selectOneRandom();
}




