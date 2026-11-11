package io.materializer.shared.utilities;

import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public final class YamlMapperUtility {

    public static final YAMLMapper MAPPER = YAMLMapper.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .disable(YAMLGenerator.Feature.SPLIT_LINES)
            .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
            .build();

    private YamlMapperUtility() { }
}
