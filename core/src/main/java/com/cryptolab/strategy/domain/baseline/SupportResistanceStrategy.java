package com.cryptolab.strategy.domain.baseline;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.StrategyDescriptor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class SupportResistanceStrategy implements Strategy {

    public static final String TYPE = "SR";
    public static final String VERSION = "1.0";

    private final int window;

    public SupportResistanceStrategy(int window) {
        if (window < 2) {
            throw new IllegalArgumentException("window must be at least 2");
        }
        this.window = window;
    }

    @Override
    public StrategyDescriptor descriptor() {
        return new StrategyDescriptor(TYPE, VERSION, Map.of("window", window));
    }

    @Override
    public Signal analyze(StrategyContext context) {
        List<Candle> candles = context.candles();
        if (candles.size() < window + 1) {
            return BaselineStrategySupport.hold(context, "insufficient candles for support/resistance");
        }

        int currentIndex = candles.size() - 1;
        int from = currentIndex - window;
        BigDecimal support = candles.get(from).low();
        BigDecimal resistance = candles.get(from).high();
        for (int index = from + 1; index < currentIndex; index++) {
            support = support.min(candles.get(index).low());
            resistance = resistance.max(candles.get(index).high());
        }

        BigDecimal currentClose = candles.get(currentIndex).close();
        if (currentClose.compareTo(support) <= 0) {
            return BaselineStrategySupport.buy(context, "close reached rolling support");
        }
        if (currentClose.compareTo(resistance) >= 0) {
            return BaselineStrategySupport.sell(context, "close reached rolling resistance");
        }
        return BaselineStrategySupport.hold(context, "close remains between rolling support and resistance");
    }
}
