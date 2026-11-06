package com.nori.ai.gateway.core.sanitize;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ContentSanitizerTest {
    @Test void appliesEachMaskV1RuleAndPreservesOrdinaryQuestion(){
        var sanitizer=new ContentSanitizer();
        for(String original:java.util.List.of("Bearer abcdef_123","api_key=abcdef","password: hello","someone@example.test","010-1234-5678","이름: 홍길동","SENTINEL_PRIVATE_123")){
            var value=sanitizer.sanitize(original);assertThat(value.text()).doesNotContain(original);assertThat(value.applied()).isTrue();assertThat(value.ruleSetVersion()).isEqualTo("mask-v1");
        }
        assertThat(sanitizer.sanitize("장비 상태를 확인해 주세요").text()).isEqualTo("장비 상태를 확인해 주세요");
        assertThatThrownBy(()->new ContentSanitizer.Sanitized("raw","mask-v1",false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->sanitizer.sanitize(null)).isInstanceOf(NullPointerException.class);
    }
}
