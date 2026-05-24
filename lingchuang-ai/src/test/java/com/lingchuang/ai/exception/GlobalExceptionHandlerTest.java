package com.lingchuang.ai.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lingchuang.ai.common.BaseResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    @Test
    void businessExceptionShouldReturnResponseWithoutErrorStackLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            GlobalExceptionHandler handler = new GlobalExceptionHandler();

            BaseResponse<?> response = handler.businessExceptionHandler(
                    new BusinessException(ErrorCode.NOT_LOGIN_ERROR));

            assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), response.getCode());
            assertEquals(ErrorCode.NOT_LOGIN_ERROR.getMessage(), response.getMessage());
            assertTrue(appender.list.stream().noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.ERROR)));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
