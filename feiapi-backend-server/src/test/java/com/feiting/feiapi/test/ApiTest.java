package com.feiting.feiapi.test;

import cn.hutool.http.HttpUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @Author feiting
 */
@SpringBootTest
public class ApiTest {

    @Test
    public void testThirdPartyApi01(){
        String result = HttpUtil.get("https://api.vvhan.com/api/horoscope?type=scorpio&time=today");
        System.out.println(result);
    }

    @Test
    public void testThirdPartyApi02(){
        String result = HttpUtil.get("https://api.vvhan.com/api/view?type=json");
        System.out.println(result);
    }

    @Test
    public void testThirdPartyApi03(){
        String result = HttpUtil.get("https://api.vvhan.com/api/visitor.info");
        System.out.println(result);
    }

}
