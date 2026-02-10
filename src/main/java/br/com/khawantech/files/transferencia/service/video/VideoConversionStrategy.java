package br.com.khawantech.files.transferencia.service.video;

import br.com.khawantech.files.transferencia.dto.FormatoVideo;

public interface VideoConversionStrategy {
    FormatoVideo target();
    VideoConversionProfile profile();
}
