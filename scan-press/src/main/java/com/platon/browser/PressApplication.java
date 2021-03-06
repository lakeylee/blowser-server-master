package com.platon.browser;

import com.alibaba.fastjson.JSON;
import com.platon.browser.bean.TransactionBean;
import com.platon.browser.dao.entity.*;
import com.platon.browser.dao.mapper.ConfigMapper;
import com.platon.browser.dao.mapper.NetworkStatMapper;
import com.platon.browser.elasticsearch.dto.DelegationReward;
import com.platon.browser.elasticsearch.dto.Transaction;
import com.platon.browser.exception.BlockNumberException;
import com.platon.browser.exception.GracefullyShutdownException;
import com.platon.browser.queue.event.TokenEvent;
import com.platon.browser.queue.publisher.*;
import com.platon.browser.service.BlockResult;
import com.platon.browser.service.DataGenService;
import com.platon.browser.service.StakeResult;
import com.platon.browser.utils.CommonUtil;
import com.platon.browser.utils.CounterBean;
import com.platon.browser.utils.GracefullyUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@EnableRetry
@EnableScheduling
@EnableConfigurationProperties
@MapperScan(basePackages = {"com.platon.browser.dao.mapper"})
@SpringBootApplication
public class PressApplication implements ApplicationRunner {

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(1);

    @Autowired
    private BlockPublisher blockPublisher;

    @Autowired
    private TransactionPublisher transactionPublisher;

    @Autowired
    private NodeOptPublisher nodeOptPublisher;

    @Autowired
    private AddressPublisher addressPublisher;

    @Autowired
    private NodePublisher nodePublisher;

    @Autowired
    private StakePublisher stakePublisher;

    @Autowired
    private DelegationPublisher delegationPublisher;

    @Autowired
    private ProposalPublisher proposalPublisher;

    @Autowired
    private VotePublisher votePublisher;

    @Autowired
    private RpPlanPublisher rpPlanPublisher;

    @Autowired
    private RewardPublisher rewardPublisher;

    @Autowired
    private EstimatePublisher estimatePublisher;

    @Autowired
    private TokenPublisher tokenPublisher;

    @Autowired
    private TokenHolderPublisher tokenHolderPublisher;

    @Autowired
    private TokenInventoryPublisher tokenInventoryPublisher;


    @Autowired
    private SlashPublisher slashPublisher;

    @Autowired
    private DataGenService dataGenService;

    @Autowired
    private NetworkStatMapper networkStatMapper;

    @Autowired
    private ConfigMapper configMapper;

    @Value("${platon.nodeMaxCount}")
    private long nodeMaxCount;

    @Value("${platon.stakeMaxCount}")
    private long stakeMaxCount;

    @Value("${platon.delegateMaxCount}")
    private long delegateMaxCount;

    @Value("${platon.proposalMaxCount}")
    private long proposalMaxCount;

    @Value("${platon.voteMaxCount}")
    private long voteMaxCount;

    @Value("${platon.rpplanMaxCount}")
    private long rpplanMaxCount;

    @Value("${platon.rewardMaxCount}")
    private long rewardMaxCount;

    @Value("${platon.slashMaxCount}")
    private long slashMaxCount;

    @Value("${platon.blockMaxCount}")
    private long blockMaxCount;

    @Value("${platon.nodeoptMaxCount}")
    private long nodeoptMaxCount;

    @Value("${platon.estimateMaxCount}")
    private long estimateMaxCount;

    @Value("${platon.tokenMaxCount}")
    private long tokenMaxCount;

    @Value("${platon.tokenERC20MaxCount}")
    private long tokenERC20MaxCount;

    @Value("${platon.tokenERC721MaxCount}")
    private long tokenERC721MaxCount;


    @Value("${platon.addressCountPerToken}")
    private int addressCountPerToken;

    @Value("${platon.tokenTransferMaxCount}")
    private long tokenTransferMaxCount;

    private long currentNodeSum = 0L;

    private long currentStakeSum = 0L;

    private long currentDelegationSum = 0L;

    private long currentProposalSum = 0L;

    private long currentVoteSum = 0L;

    private long currentRpplanSum = 0L;

    private long currentRewardSum = 0L;

    private long currentSlashSum = 0L;

    private long currentEstimateSum = 0L;

    private long currentTokenCount = 0L;

    private long currentTokenERC20Count = 0L;

    private long currentTokenERC721Count = 0L;

    private long currentTokenTransferCount = 0L;

    public static long currentTransferCount = 0L;

    private long time = 0L;

    public static void main(String[] args) {
        SpringApplication.run(PressApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws IOException, BlockNumberException {
        long startTime = System.currentTimeMillis();
        BigInteger blockNumber = init();
        NetworkStatExample networkStatExample = new NetworkStatExample();
        networkStatExample.createCriteria().andIdEqualTo(1);
        while (true) {

            if (blockPublisher.getTotalCount() > blockMaxCount) {
                log.warn("??????????????????????????????{}", blockMaxCount);
                System.exit(0);
                break;
            }

            // ??????????????????
            checkAppStatus(blockNumber, startTime);
            // ???????????????&??????&?????????????????????
            BlockResult blockResult = makeBlock(blockNumber);
            // ???????????????&???????????????
            makeStake(blockResult);
            // ????????????????????????
            //makeDelegation(blockResult);
            // ????????????????????????
            //makeProposal(blockResult);
            // ????????????????????????
            //makeVote(blockResult);
            // ?????????rpplan?????????
            //makeRpPlan(blockResult);
            // ?????????slash?????????
            //makeSlash(blockResult);
            // ??????????????????????????????
            makeReward(blockResult);
            // ?????????gas?????????
            makeEstimate(blockResult);
            // ?????????token?????????
            makeToken(blockResult);
            // ???????????????
            blockNumber = blockNumber.add(BigInteger.ONE);
            log.info("???????????????" + blockNumber);

            if (blockNumber.intValue() % 1024 == 0) {
                flushCount(blockNumber, startTime);
            }

        }
    }

    private BigInteger init() {
        GracefullyUtil.updateStatus();
        log.warn("??????????????????:counter.json");
        String path = CommonUtil.isWin() ? "/browser-press/counter.json" : "counter.json";
        File counterFile = FileUtils.getFile(System.getProperty("user.dir"), path);
        CounterBean counterBean = new CounterBean();
        BigInteger blockNumber = BigInteger.ZERO;
        try {
            String status = FileUtils.readFileToString(counterFile, "UTF8");
            counterBean = JSON.parseObject(status, CounterBean.class);
            blockPublisher.setTotalCount(counterBean.getBlockCount());
            transactionPublisher.setTotalCount(counterBean.getTransactionCount());
            nodeOptPublisher.setTotalCount(counterBean.getNodeOptCount());
            dataGenService.setNodeOptMaxId(counterBean.getNodeOptMaxId());
            addressPublisher.setTotalCount(counterBean.getAddressCount());
            currentNodeSum = counterBean.getNodeCount();
            currentStakeSum = counterBean.getStakingCount();
            currentDelegationSum = counterBean.getDelegationCount();
            currentProposalSum = counterBean.getProposalCount();
            currentVoteSum = counterBean.getVoteCount();
            currentRpplanSum = counterBean.getRpplanCount();
            currentRewardSum = counterBean.getRewardCount();
            currentSlashSum = counterBean.getSlashCount();
            currentEstimateSum = counterBean.getEstimateCount();
            currentTokenCount = counterBean.getTokenCount();
            currentTokenTransferCount = counterBean.getTokenTransferCount();
            currentTransferCount = counterBean.getTransactionCount();
            time = CommonUtil.resolving(counterBean.getTime());
            blockNumber = BigInteger.valueOf(counterBean.getLastBlockNumber());
        } catch (IOException e) {
            log.warn("??????????????????,????????????!");
        }
        log.warn("??????????????????:{}", JSON.toJSONString(counterBean, true));

        // ??????????????????
        configMapper.deleteByExample(null);
        configMapper.batchInsert(dataGenService.getConfigList());
        // ????????????????????????
        networkStatMapper.deleteByExample(null);
        dataGenService.getNetworkStat().setCurNumber(0L);
        networkStatMapper.insert(dataGenService.getNetworkStat());
        return blockNumber;
    }

    private void checkAppStatus(BigInteger blockNumber, long startTime) throws IOException {
        try {
            GracefullyUtil.monitor();
        } catch (GracefullyShutdownException | InterruptedException e) {
            log.warn("?????????SHUTDOWN??????,????????????????????????,??????????????????...");
            flushCount(blockNumber, startTime);
            System.exit(0);
        }
    }

    private void flushCount(BigInteger blockNumber, long startTime) {
        long endTime = System.currentTimeMillis();
        CounterBean counter = new CounterBean();
        counter.setBlockCount(blockPublisher.getTotalCount());
        counter.setTransactionCount(transactionPublisher.getTotalCount());
        counter.setNodeOptCount(nodeOptPublisher.getTotalCount());
        counter.setNodeOptMaxId(dataGenService.getNodeOptMaxId());
        counter.setAddressCount(addressPublisher.getTotalCount());
        counter.setLastBlockNumber(blockNumber.longValue());
        counter.setNodeCount(currentNodeSum);
        counter.setStakingCount(currentStakeSum);
        counter.setDelegationCount(delegationPublisher.getTotalCount());
        counter.setProposalCount(currentProposalSum);
        counter.setVoteCount(currentVoteSum);
        counter.setRpplanCount(currentRpplanSum);
        counter.setSlashCount(currentSlashSum);
        counter.setRewardCount(currentRewardSum);
        counter.setEstimateCount(currentEstimateSum);
        counter.setTokenCount(tokenPublisher.getTotalCount());
        counter.setTokenHolderCount(tokenHolderPublisher.getTotalCount());
        counter.setTokenInventoryCount(tokenInventoryPublisher.getTotalCount());
        counter.setTokenTransferCount(currentTokenTransferCount);
        counter.setTime(CommonUtil.getTime(time, startTime, endTime));
        String status = JSON.toJSONString(counter, true);
        String path = CommonUtil.isWin() ? "/browser-press/counter.json" : "counter.json";
        File counterFile = FileUtils.getFile(System.getProperty("user.dir"), path);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(counterFile))) {
            bw.write(status);
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.warn("??????????????????,???????????????:{}", status);
    }

    /**
     * ?????????????????????????????????
     *
     * @param blockNumber
     * @return
     */
    private BlockResult makeBlock(BigInteger blockNumber) {
        BlockResult blockResult = dataGenService.getBlockResult(blockNumber, nodeMaxCount);
        dataGenService.getNetworkStat().setCurNumber(blockNumber.longValue());
        blockPublisher.publish(Arrays.asList(blockResult.getBlock()));
        TransactionBean transactionBean = new TransactionBean();
        transactionBean.setBlockNum(blockNumber);
        transactionBean.setTransactionList(blockResult.getTransactionList());
        transactionPublisher.publish(transactionBean);
        if (nodeOptPublisher.getTotalCount() <= nodeoptMaxCount) {
            //???????????????????????????????????????????????????ES
            nodeOptPublisher.publish(blockResult.getNodeOptList());
        }
        return blockResult;
    }

    /**
     * ????????????&??????
     *
     * @param blockResult
     */
    private boolean nodeSatisfied = false;

    private boolean stakeSatisfied = false;

    private void makeStake(BlockResult blockResult) throws BlockNumberException {
        List<Node> nodeList = new ArrayList<>();
        List<Staking> stakingList = new ArrayList<>();
        for (Transaction tx : blockResult.getTransactionList()) {
            if (
                    (
                            tx.getTypeEnum() == Transaction.TypeEnum.STAKE_CREATE ||
                                    tx.getTypeEnum() == Transaction.TypeEnum.STAKE_MODIFY ||
                                    tx.getTypeEnum() == Transaction.TypeEnum.STAKE_INCREASE ||
                                    tx.getTypeEnum() == Transaction.TypeEnum.STAKE_EXIT
                    ) &&
                            (!nodeSatisfied || !stakeSatisfied)
            ) {
                // bigInteger[0].intValue()=stakeMaxCount/nodeMaxCount ?????????????????????
                BigInteger[] bigInteger = BigInteger.valueOf(stakeMaxCount).divideAndRemainder(BigInteger.valueOf(nodeMaxCount));
                StakeResult stakeResult = dataGenService.getStakeResult(tx, currentNodeSum, bigInteger[0].intValue());
                if (currentNodeSum < nodeMaxCount) {
                    // ??????????????????????????????????????????
                    nodeList.add(stakeResult.getNode());
                    currentNodeSum++;
                } else {
                    nodeSatisfied = true;
                }
                if (currentStakeSum < stakeMaxCount) {
                    // ??????????????????????????????????????????
                    stakingList.addAll(stakeResult.getStakingList());
                    //stakingList.add(stakeResult.getStaking());
                    currentStakeSum += stakeResult.getStakingList().size();
                    //currentStakeSum++;
                } else {
                    stakeSatisfied = true;
                }
            }
        }

        if (!nodeList.isEmpty()) {
            nodePublisher.publish(nodeList);
        }
        if (!stakingList.isEmpty()) {
            stakePublisher.publish(stakingList);
        }
    }

    /**
     * ????????????
     *
     * @param blockResult
     */
    private void makeDelegation(BlockResult blockResult) {
        if (currentDelegationSum < delegateMaxCount) {
            // ??????????????????????????????????????????
            List<Delegation> delegationList = new ArrayList<>();
            for (Transaction tx : blockResult.getTransactionList()) {
                if (
                        tx.getTypeEnum() == Transaction.TypeEnum.DELEGATE_CREATE ||
                                tx.getTypeEnum() == Transaction.TypeEnum.DELEGATE_EXIT
                ) {
                    delegationList.add(dataGenService.getDelegation(tx));
                    currentDelegationSum++;
                }
            }
            delegationPublisher.publish(delegationList);
        }
    }

    /**
     * ????????????
     *
     * @param blockResult
     */
    private void makeProposal(BlockResult blockResult) {
        if (currentProposalSum < proposalMaxCount) {
            // ??????????????????????????????????????????
            List<Proposal> proposalList = new ArrayList<>();
            for (Transaction tx : blockResult.getTransactionList()) {
                if (
                        tx.getTypeEnum() == Transaction.TypeEnum.PROPOSAL_CANCEL ||
                                tx.getTypeEnum() == Transaction.TypeEnum.PROPOSAL_TEXT ||
                                tx.getTypeEnum() == Transaction.TypeEnum.PROPOSAL_PARAMETER ||
                                tx.getTypeEnum() == Transaction.TypeEnum.PROPOSAL_UPGRADE
                ) {
                    proposalList.add(dataGenService.getProposal(tx));
                    currentProposalSum++;
                }
            }
            proposalPublisher.publish(proposalList);
        }
    }

    /**
     * ????????????
     *
     * @param blockResult
     */
    private void makeVote(BlockResult blockResult) {
        if (currentVoteSum < voteMaxCount) {
            // ??????????????????????????????????????????
            List<Vote> voteList = new ArrayList<>();
            for (Transaction tx : blockResult.getTransactionList()) {
                if (
                        tx.getTypeEnum() == Transaction.TypeEnum.PROPOSAL_VOTE
                ) {
                    voteList.add(dataGenService.getVote(tx));
                    currentVoteSum++;
                }
            }
            votePublisher.publish(voteList);
        }
    }

    /**
     * ??????Slash
     *
     * @param blockResult
     */
    private void makeSlash(BlockResult blockResult) {
        if (currentSlashSum < slashMaxCount) {
            // ?????????????????????Slash???????????????
            List<Slash> slashList = new ArrayList<>();
            for (Transaction tx : blockResult.getTransactionList()) {
                if (
                        tx.getTypeEnum() == Transaction.TypeEnum.REPORT
                ) {
                    slashList.add(dataGenService.getSlash(tx));
                    currentSlashSum++;
                }
            }
            slashPublisher.publish(slashList);
        }
    }

    /**
     * ??????RpPlan
     *
     * @param blockResult
     */
    private void makeRpPlan(BlockResult blockResult) throws BlockNumberException {
        if (currentRpplanSum < rpplanMaxCount) {
            // ?????????????????????RpPlan???????????????
            List<RpPlan> rpPlanList = new ArrayList<>();
            for (Transaction tx : blockResult.getTransactionList()) {
                if (
                        tx.getTypeEnum() == Transaction.TypeEnum.RESTRICTING_CREATE
                ) {
                    rpPlanList.add(dataGenService.getRpPlan(tx));
                    currentRpplanSum++;
                }
            }
            rpPlanPublisher.publish(rpPlanList);
        }
    }

    /**
     * ??????????????????
     *
     * @param blockResult
     */
    private void makeReward(BlockResult blockResult) throws BlockNumberException {
        if (currentRewardSum < rewardMaxCount) {
            // ????????????????????????????????????????????????
            List<DelegationReward> delegationRewards = new ArrayList<>();
            for (Transaction tx : blockResult.getTransactionList()) {
                if (
                        tx.getTypeEnum() == Transaction.TypeEnum.CLAIM_REWARDS
                ) {
                    delegationRewards.add(dataGenService.getReward(tx));
                    currentRewardSum++;
                }
            }
            rewardPublisher.publish(delegationRewards);
        }
    }

    /**
     * ??????gas
     *
     * @param blockResult
     */
    private void makeEstimate(BlockResult blockResult) throws BlockNumberException {
        if (currentEstimateSum < estimateMaxCount) {
            // ????????????????????????????????????????????????
            List<GasEstimate> gasEstimates = new ArrayList<>();
            for (Transaction tx : blockResult.getTransactionList()) {
                if (
                        tx.getTypeEnum() == Transaction.TypeEnum.DELEGATE_CREATE
                ) {
                    gasEstimates.add(dataGenService.getEstimate(tx));
                    currentEstimateSum++;
                }
            }
            estimatePublisher.publish(gasEstimates);
        }
    }

    private void makeToken(BlockResult blockResult) {
        if (currentTokenCount < tokenMaxCount) {
            TokenEvent tokenEvent = new TokenEvent();
            List<TokenHolder> tokenHolderList = new ArrayList<>();
            List<TokenInventory> tokenInventoryList = new ArrayList<>();
            for (Transaction tx : blockResult.getTransactionList()) {
                // erc20???erc721???????????????mysql
                if (tx.getType() == Transaction.TypeEnum.ERC20_CONTRACT_CREATE.getCode()) {
                    tokenEvent.getTokenList().add(dataGenService.getToken(tx));
                    tokenHolderList.add(dataGenService.getTokenHolder(tx));
                    currentTokenCount++;
                }
                if (tx.getType() == Transaction.TypeEnum.ERC721_CONTRACT_CREATE.getCode()) {
                    tokenEvent.getTokenList().add(dataGenService.getToken(tx));
                    tokenHolderList.add(dataGenService.getTokenHolder(tx));
                    tokenInventoryList.add(dataGenService.getTokenInventory(tx));
                    currentTokenCount++;
                }
                // erc20???erc721???????????????Redis???es
                if (tx.getType() == Transaction.TypeEnum.ERC20_CONTRACT_EXEC.getCode()) {
                    tokenEvent.getErc20TxList().add(dataGenService.getTokenTX(tx));
                }
                if (tx.getType() == Transaction.TypeEnum.ERC721_CONTRACT_EXEC.getCode()) {
                    tokenEvent.getErc721TxList().add(dataGenService.getTokenTX(tx));
                }
            }
            tokenPublisher.publish(tokenEvent);
            tokenHolderPublisher.publish(tokenHolderList);
            tokenInventoryPublisher.publish(tokenInventoryList);
        }
    }

}
