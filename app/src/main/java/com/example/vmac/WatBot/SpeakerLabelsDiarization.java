package com.example.vmac.WatBot;

/**
 * Created by VMac on 17/05/17.
 */

import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeakerLabelsResult;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechRecognitionAlternative;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechRecognitionResult;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechRecognitionResults;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechTimestamp;
import com.ibm.watson.developer_cloud.speech_to_text.v1.websocket.BaseRecognizeCallback;
import com.ibm.watson.developer_cloud.util.GsonSingleton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class SpeakerLabelsDiarization {
    public static class RecoToken {
        private Double startTime;
        private Double endTime;
        private Long speaker;
        private String word;
        private Boolean spLabelIsFinal;


        /**
         * Instantiates a new reco token.
         *
         * @param speechTimestamp the speech timestamp
         */
        RecoToken(SpeechTimestamp speechTimestamp) {
            startTime = speechTimestamp.getStartTime();
            endTime = speechTimestamp.getEndTime();
            word = speechTimestamp.getWord();
        }

        /**
         * Instantiates a new reco token.
         *
         * @param speakerLabel the speaker label
         */
        RecoToken(SpeakerLabelsResult speakerLabel) {
            startTime = Double.valueOf(speakerLabel.getFrom());
            endTime = Double.valueOf(speakerLabel.getTo());
            speaker = speakerLabel.getSpeaker();
        }

        /**
         * Update from.
         *
         * @param speechTimestamp the speech timestamp
         */
        public void updateFrom(SpeechTimestamp speechTimestamp) {
            word = speechTimestamp.getWord();
        }

        /**
         * Update from.
         *
         * @param speakerLabel the speaker label
         */
        public void updateFrom(SpeakerLabelsResult speakerLabel) {
            speaker = speakerLabel.getSpeaker();
        }
    }

    /**
     * The Class Utterance.
     */
    public static class Utterance {
        private Integer speaker;
        private String transcript = "";

        /**
         * Instantiates a new utterance.
         *
         * @param speaker    the speaker
         * @param transcript the transcript
         */
        public Utterance(final Integer speaker, final String transcript) {
            this.speaker = speaker;
            this.transcript = transcript;
        }
    }

    /**
     * The Class RecoTokens.
     */
    public static class RecoTokens {

        private Map<Double, RecoToken> recoTokenMap;

        /**
         * Instantiates a new reco tokens.
         */
        public RecoTokens() {
            recoTokenMap = new LinkedHashMap<Double, RecoToken>();
        }

        /**
         * Adds the.
         *
         * @param speechResults the speech results
         */
        public void add(SpeechRecognitionResults speechResults) {
            if (speechResults.getResults() != null)
                for (int i = 0; i < speechResults.getResults().size(); i++) {
                    SpeechRecognitionResult transcript = speechResults.getResults().get(i);
                    if (transcript.isFinalResults()) {
                        SpeechRecognitionAlternative speechAlternative = transcript.getAlternatives().get(0);

                        for (int ts = 0; ts < speechAlternative.getTimestamps().size(); ts++) {
                            SpeechTimestamp speechTimestamp = speechAlternative.getTimestamps().get(ts);
                            add(speechTimestamp);
                        }
                    }
                }
            if (speechResults.getSpeakerLabels() != null)
                for (int i = 0; i < speechResults.getSpeakerLabels().size(); i++) {
                    add(speechResults.getSpeakerLabels().get(i));
                }

        }

        /**
         * Adds the.
         *
         * @param speechTimestamp the speech timestamp
         */
        public void add(SpeechTimestamp speechTimestamp) {
            RecoToken recoToken = recoTokenMap.get(speechTimestamp.getStartTime());
            if (recoToken == null) {
                recoToken = new RecoToken(speechTimestamp);
                recoTokenMap.put(speechTimestamp.getStartTime(), recoToken);
            } else {
                recoToken.updateFrom(speechTimestamp);
            }
        }

        /**
         * Adds the.
         *
         * @param speakerLabel the speaker label
         */
        public void add(SpeakerLabelsResult speakerLabel) {
            RecoToken recoToken = recoTokenMap.get(speakerLabel.getFrom());
            if (recoToken == null) {
                recoToken = new RecoToken(speakerLabel);
                recoTokenMap.put(Double.valueOf(speakerLabel.getFrom()), recoToken);
            } else {
                recoToken.updateFrom(speakerLabel);
            }

            if (speakerLabel.isFinalResults()) {
                markTokensBeforeAsFinal(speakerLabel.getFrom());
                report();
                cleanFinal();
            }
        }

        private void markTokensBeforeAsFinal(Float from) {
            Map<Double, RecoToken> recoTokenMap = new LinkedHashMap<>();

            for (RecoToken rt : recoTokenMap.values()) {
                if (rt.startTime <= from)
                    rt.spLabelIsFinal = true;
            }
        }

        /**
         * Report.
         */
        public void report() {
            List<Utterance> uttterances = new ArrayList<Utterance>();
            Utterance currentUtterance = new Utterance(0, "");

            for (RecoToken rt : recoTokenMap.values()) {
                if (currentUtterance.speaker != Math.toIntExact(rt.speaker)) {
                    uttterances.add(currentUtterance);
                    currentUtterance = new Utterance(Math.toIntExact(rt.speaker), "");
                }
                currentUtterance.transcript = currentUtterance.transcript + rt.word + " ";
            }
            uttterances.add(currentUtterance);

            String result = GsonSingleton.getGson().toJson(uttterances);
            System.out.println(result);
        }

        private void cleanFinal() {
            Set<Map.Entry<Double, RecoToken>> set = recoTokenMap.entrySet();
            for (Map.Entry<Double, RecoToken> e : set) {
                if (e.getValue().spLabelIsFinal) {
                    recoTokenMap.remove(e.getKey());
                }
            }
        }

    }


    private static CountDownLatch lock = new CountDownLatch(1);
}