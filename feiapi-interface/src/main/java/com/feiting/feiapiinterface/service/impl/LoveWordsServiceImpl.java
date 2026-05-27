package com.feiting.feiapiinterface.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feiting.feiapiinterface.model.LoveWords;
import com.feiting.feiapiinterface.service.LoveWordsService;
import com.feiting.feiapiinterface.mapper.LoveWordsMapper;
import org.springframework.stereotype.Service;

/**
* @author asus
* @description 针对表【love_words】的数据库操作Service实现
* @createDate 2023-03-14 18:02:28
*/
@Service
public class LoveWordsServiceImpl extends ServiceImpl<LoveWordsMapper, LoveWords>
    implements LoveWordsService{

    @Override
    public LoveWords getOneRandom() {
        return baseMapper.selectOneRandom();
    }
}




