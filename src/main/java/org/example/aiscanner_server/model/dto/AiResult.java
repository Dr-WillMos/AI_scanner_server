package org.example.aiscanner_server.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AiResult {

    @JsonProperty("aiGlitchProb")
    private double aiGlitchProb;

    @JsonProperty("violenceProb")
    private double violenceProb;

    private String transcription;

    @JsonProperty("keywordHit")
    private boolean keywordHit;

    public double getAiGlitchProb() { return aiGlitchProb; }
    public void setAiGlitchProb(double aiGlitchProb) { this.aiGlitchProb = aiGlitchProb; }

    public double getViolenceProb() { return violenceProb; }
    public void setViolenceProb(double violenceProb) { this.violenceProb = violenceProb; }

    public String getTranscription() { return transcription; }
    public void setTranscription(String transcription) { this.transcription = transcription; }

    public boolean isKeywordHit() { return keywordHit; }
    public void setKeywordHit(boolean keywordHit) { this.keywordHit = keywordHit; }
}
