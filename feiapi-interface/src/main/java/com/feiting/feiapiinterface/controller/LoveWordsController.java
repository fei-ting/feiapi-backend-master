package com.feiting.feiapiinterface.controller;

import com.feiting.feiapiinterface.model.LoveWords;
import com.feiting.feiapiinterface.service.LoveWordsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.Resource;

/**
 * @Author feiting
 */
@RestController
@RequestMapping("/love_words")
public class LoveWordsController {

    @Resource
    private LoveWordsService loveWordsService;

    @GetMapping
    public String getLoveWords() {
        LoveWords loveWords = loveWordsService.getOneRandom();
        if (loveWords == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "暂无土味情话数据");
        }
        return loveWords.getWords();
    }

}
