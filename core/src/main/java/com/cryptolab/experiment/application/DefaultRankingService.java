package com.cryptolab.experiment.application;

import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.Ranking;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DefaultRankingService {

    private static final Comparator<Evaluation> ORDER = Comparator
            .comparing((Evaluation evaluation) -> evaluation.metrics().score(), Comparator.reverseOrder())
            .thenComparing(evaluation -> evaluation.metrics().totalReturnPct(), Comparator.reverseOrder())
            .thenComparing(evaluation -> evaluation.metrics().maxDrawdownPct().abs())
            .thenComparing(evaluation -> evaluation.experimentId().toString());

    public List<Ranking> rank(List<Evaluation> evaluations) {
        List<Evaluation> ordered = new ArrayList<>(evaluations == null ? List.of() : evaluations);
        ordered.sort(ORDER);
        List<Ranking> rankings = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            Evaluation evaluation = ordered.get(index);
            rankings.add(new Ranking(index + 1, evaluation.experimentId(), evaluation.metrics()));
        }
        return List.copyOf(rankings);
    }
}
