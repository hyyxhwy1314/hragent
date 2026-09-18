package org.example.hragent.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.annotation.RateLimit;
import org.example.hragent.dto.ContractReviewDto;
import org.example.hragent.service.ContractReviewService;
import org.example.hragent.vo.ContractReviewVO;
import org.example.hragent.vo.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 合同审查 Controller
 * AI 审查耗时长、大模型调用成本高，需限流防止刷接口
 */
@Slf4j
@RestController
@RequestMapping("/contract-review")
public class ContractReviewController {

    private final ContractReviewService contractReviewService;

    public ContractReviewController(ContractReviewService contractReviewService) {
        this.contractReviewService = contractReviewService;
    }

    @PostMapping
    @RateLimit(rate = 3, rateInterval = 10, rateIntervalUnit = TimeUnit.SECONDS,
            message = "审查请求过于频繁，请稍后再试")
    public R<ContractReviewVO> review(@Valid @RequestBody ContractReviewDto dto) {
        log.info("收到合同审查请求 contractName={}, 文本长度={}",
                dto.getContractName(), dto.getContent() == null ? 0 : dto.getContent().length());
        ContractReviewVO vo = contractReviewService.review(dto.getContent(), dto.getContractName());
        return R.ok(vo);
    }
}