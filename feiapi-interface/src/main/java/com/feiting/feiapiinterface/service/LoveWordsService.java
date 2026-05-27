package com.feiting.feiapiinterface.service;

import com.feiting.feiapiinterface.model.LoveWords;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author asus
* @description 针对表【love_words】的数据库操作Service
* @createDate 2023-03-14 18:02:28
*/
public interface LoveWordsService extends IService<LoveWords> {

    /**
     * 随机获取一条土味情话
     *
     * @return 随机土味情话，表为空时返回 null
     */
    LoveWords getOneRandom();
}
