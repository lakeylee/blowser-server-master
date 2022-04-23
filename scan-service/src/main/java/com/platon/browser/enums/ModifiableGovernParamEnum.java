package com.platon.browser.enums;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 *  可修改的治理参数枚举
 */
public enum ModifiableGovernParamEnum {
	STAKE_THRESHOLD("staking", "stakeThreshold"),//质押门槛
	OPERATING_THRESHOLD("staking", "operatingThreshold"),//委托门槛
	MAX_VALIDATORS("staking", "maxValidators"),//委托门槛
	UN_STAKE_FREEZE_DURATION("staking", "unStakeFreezeDuration"),//解质押需要经过的结算周期数
	SLASH_FRACTION_DUPLICATE_SIGN("slashing", "slashFractionDuplicateSign"),//双签处罚万分比
	DUPLICATE_SIGN_REPORT_REWARD("slashing", "duplicateSignReportReward"),//双签奖励百分比
	MAX_EVIDENCE_AGE("slashing", "maxEvidenceAge"),//双签奖励百分比
	SLASH_BLOCKS_REWARD("slashing", "slashBlocksReward"),//低出块率处罚区块奖励数
	MAX_BLOCK_GAS_LIMIT("block", "maxBlockGasLimit"),//区块最大Gas限制
	ZERO_PRODUCE_NUMBER_THRESHOLD("slashing", "zeroProduceNumberThreshold"),//零出块次数阈值，在指定时间范围内达到该次数则处罚
	ZERO_PRODUCE_CUMULATIVE_TIME("slashing", "zeroProduceCumulativeTime"),//上一次零出块后，在往后的N个共识周期内如若再出现零出块，则在这N个共识周期完成时记录零出块信息
	ZERO_PRODUCE_FREEZE_DURATION("slashing", "zeroProduceFreezeDuration"),//节点零出块惩罚被锁定时间
	REWARD_PER_MAX_CHANGE_RANGE("staking", "rewardPerMaxChangeRange"),
	REWARD_PER_CHANGE_INTERVAL("staking", "rewardPerChangeInterval"),
	INCREASE_ISSUANCE_RATIO("reward", "increaseIssuanceRatio"),
	RESTRICTING_MINIMUM_RELEASE("restricting", "minimumRelease") // 锁仓最小释放金额 v0.14.0版本新增
	;

	private String module;

	private String name;

	ModifiableGovernParamEnum(String module, String name) {
		this.module = module;
		this.name = name;
	}

	public String getModule() {
		return module;
	}
	public String getName() {
		return name;
	}

	private static final Map<String,ModifiableGovernParamEnum> MAP = new HashMap<>();
	public static Map<String,ModifiableGovernParamEnum> getMap(){return MAP;}
	static {
		Arrays.asList(ModifiableGovernParamEnum.values()).forEach(paramEnum-> MAP.put(paramEnum.getName(),paramEnum));
	}
}
