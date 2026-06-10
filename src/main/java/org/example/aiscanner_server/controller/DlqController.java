package org.example.aiscanner_server.controller;

import org.example.aiscanner_server.common.ApiResponse;
import org.example.aiscanner_server.model.dto.DlqMessage;
import org.example.aiscanner_server.service.DlqService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
/**死信队列相关，用于因未知原因被AI处理失败的消息*/
@RestController
@RequestMapping("/api/v1/dlq")
public class DlqController {

    private final DlqService dlqService;

    public DlqController(DlqService dlqService) {
        this.dlqService = dlqService;
    }

    @GetMapping
    public ApiResponse<List<DlqMessage>> listMessages( //列出所有死信
            @RequestParam(defaultValue = "50") int count) {
        return ApiResponse.ok(dlqService.listMessages(count));
    }

    @GetMapping("/{messageId}") //调阅指定的死信
    public ApiResponse<DlqMessage> getMessage(@PathVariable String messageId) {
        DlqMessage msg = dlqService.getMessage(messageId);
        if (msg == null) {
            return ApiResponse.error(404, "DLQ message not found");
        }
        return ApiResponse.ok(msg);
    }

    @PostMapping("/{messageId}/retry")  //对死信进行重试操作
    public ApiResponse<DlqMessage> retryMessage(@PathVariable String messageId) {
        DlqMessage msg = dlqService.retryTask(messageId);
        if (msg == null) {
            return ApiResponse.error(404, "DLQ message not found");
        }
        return ApiResponse.ok(msg);
    }

    @DeleteMapping("/{messageId}")  //删除死信
    public ApiResponse<Void> deleteMessage(@PathVariable String messageId) {
        boolean deleted = dlqService.deleteMessage(messageId);
        if (!deleted) {
            return ApiResponse.error(404, "DLQ message not found");
        }
        return ApiResponse.ok();
    }

    @GetMapping("/stats")   //管理侧统计监控端点
    public ApiResponse<Map<String, Object>> stats() {
        long total = dlqService.getPendingCount();  //待处理死信总数
        long finalDead = dlqService.getFinalDeadLetterCount(); //已确认为最终死信(无法通过重试改变死信身份)数量
        return ApiResponse.ok(Map.of(
                "totalPending", total,
                "finalDeadLetters", finalDead,
                "oldestMessageTime", dlqService.getOldestMessageTime() != null
                        ? dlqService.getOldestMessageTime() : "N/A",
                "alertThreshold", 10,
                "alertActive", total >= 10
        ));
    }

    @DeleteMapping("/purge")  //一键清空死信队列的所有信息
    public ApiResponse<Void> purge() {
        dlqService.purge();
        return ApiResponse.ok();
    }
}
