package br.com.khawantech.files.transferencia.service.video;

import br.com.khawantech.files.transferencia.dto.FormatoVideo;

public class DefaultVideoConversionStrategy implements VideoConversionStrategy {

    private final FormatoVideo target;
    private final VideoConversionProfile profile;

    public DefaultVideoConversionStrategy(FormatoVideo target, VideoConversionProfile profile) {
        this.target = target;
        this.profile = profile;
    }

    @Override
    public FormatoVideo target() {
        return target;
    }

    @Override
    public VideoConversionProfile profile() {
        return profile;
    }
}
