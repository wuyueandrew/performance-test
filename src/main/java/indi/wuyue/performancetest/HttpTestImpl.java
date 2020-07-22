package indi.wuyue.performancetest;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.util.Arrays;
import java.util.concurrent.*;

public class HttpTestImpl implements HttpTest {

    private ExecutorService service;

    private final String url;

    private final int threadCnt;

    private final int reqCnt;

    private long[] timeCost;

    private CountDownLatch startLatch;

    private CountDownLatch statLatch;

    public HttpTestImpl(String url, int threadCnt, int reqCnt) {
        this.url = url;
        this.threadCnt = threadCnt;
        this.reqCnt = reqCnt;
        this.timeCost = new long[reqCnt];
        this.startLatch = new CountDownLatch(1);
        this.statLatch = new CountDownLatch(threadCnt);
        service = Executors.newFixedThreadPool(threadCnt);
    }

    @Override
    public void test() {
        for (int i = 0; i < threadCnt; i++) {
            service.submit(new Req(i, startLatch, statLatch, url, reqCnt / threadCnt, timeCost));
        }
        startLatch.countDown();
        try {
            statLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        stat();
    }

    private void stat() {
        Arrays.sort(timeCost);
        double avg = Arrays.stream(timeCost).average().getAsDouble();
        System.out.println(String.format("%d个线程，请求%d次，平均响应时间：%fms, 95%%响应时间：%dms", threadCnt, reqCnt, avg, timeCost[reqCnt * 95 / 100]));
    }

    class Req implements Runnable {

        private Integer threadNo;

        private CountDownLatch startLatch;

        private CountDownLatch statLatch;

        private String url;

        private int reqCnt;

        private long[] timeCost;

        private int initIndex;

        private CloseableHttpClient httpClient;

        Req(Integer threadNo, CountDownLatch startLatch, CountDownLatch statLatch, String url, int reqCnt, long[] timeCost) {
            this.threadNo = threadNo;
            this.startLatch = startLatch;
            this.statLatch = statLatch;
            this.url = url;
            this.reqCnt = reqCnt;
            this.timeCost = timeCost;
            this.initIndex = threadNo * reqCnt;
            httpClient = HttpClients.createDefault();
        }

        @Override
        public void run() {
            try {
                startLatch.await();
                for (int i = 0; i < reqCnt; i++) {
                    long start = System.currentTimeMillis();
                    req();
                    timeCost[initIndex + i] = System.currentTimeMillis() - start;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                statLatch.countDown();
            }
        }

        private void req() {
            try {
                HttpGet method = new HttpGet(url);
                RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(1000).setSocketTimeout(1000).build();
                method.setConfig(requestConfig);
                CloseableHttpResponse response = httpClient.execute(method);
                HttpEntity entity = response.getEntity();
                EntityUtils.toString(entity);
                response.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public static void main(String[] args) {
        HttpTest httpTest = new HttpTestImpl("http://www.baidu.com", 10, 1000);
        httpTest.test();
        System.exit(1);
    }

}
