package com.obra.certificaciones.controlrubro.config;

import com.obra.certificaciones.controlrubro.entity.ControlRubroEstimacion;
import com.obra.certificaciones.controlrubro.repository.ControlRubroEstimacionRepository;
import com.obra.certificaciones.obra.service.ObraService;
import com.obra.certificaciones.rubro.entity.Rubro;
import com.obra.certificaciones.rubro.repository.RubroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Configuration
@RequiredArgsConstructor
public class ControlRubroTechosIniciales {
    private final ObraService obraService;
    private final RubroRepository rubroRepository;
    private final ControlRubroEstimacionRepository estimacionRepository;

    @Bean
    ApplicationRunner cargarTechosDireccionPorRubro() {
        return args -> cargarTechos();
    }

    @Transactional
    void cargarTechos() {
        var obra = obraService.asegurarObraInicial();
        List<Rubro> rubros = rubroRepository.findAllByOrderByCodigoAscNombreAsc();
        Map<String, BigDecimal> porCodigo = techosPorCodigo();
        Map<String, BigDecimal> porNombre = techosPorNombre();

        for (Rubro rubro : rubros) {
            BigDecimal techo = porCodigo.get(rubro.getCodigo());
            if (techo == null) {
                techo = porNombre.get(normalizar(rubro.getNombre()));
            }
            if (techo == null || techo.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Optional<ControlRubroEstimacion> existente = estimacionRepository.findByObraIdAndRubroId(obra.getId(), rubro.getId());
            if (existente.isPresent() && existente.get().getTechoDireccion() != null && existente.get().getTechoDireccion().compareTo(BigDecimal.ZERO) > 0) {
                continue;
            }
            ControlRubroEstimacion estimacion = existente.orElseGet(ControlRubroEstimacion::new);
            estimacion.setObra(obra);
            estimacion.setRubro(rubro);
            estimacion.setTechoDireccion(techo);
            estimacionRepository.save(estimacion);
        }
    }

    private Map<String, BigDecimal> techosPorCodigo() {
        Map<String, BigDecimal> valores = new LinkedHashMap<>();
        valores.put("1", monto("130893956.20"));
        valores.put("2", monto("14610216.39"));
        valores.put("3.1", monto("35079730.49"));
        valores.put("3.2", monto("575107218.88"));
        valores.put("3.3", monto("300060703.03"));
        valores.put("3.4", monto("84903359.56"));
        valores.put("3.5", monto("368636500.07"));
        valores.put("3.6", monto("62693421.93"));
        valores.put("3.7", monto("397208021.86"));
        valores.put("3.8", monto("243354256.55"));
        valores.put("3.9", monto("111230279.67"));
        valores.put("3.10", monto("515512400.50"));
        valores.put("4", monto("706139338.44"));
        valores.put("6", monto("834997574.19"));
        valores.put("7", monto("211226183.35"));
        valores.put("8", monto("383475690.39"));
        valores.put("9", monto("627575669.38"));
        valores.put("10", monto("236850722.91"));
        valores.put("11", monto("142880098.91"));
        valores.put("12", monto("181159379.47"));
        valores.put("13", monto("215358879.28"));
        valores.put("14", monto("238356551.63"));
        valores.put("15", monto("315023510.40"));
        valores.put("16", monto("162234796.31"));
        valores.put("17", monto("58122569.10"));
        valores.put("18", monto("3363070.25"));
        return valores;
    }

    private Map<String, BigDecimal> techosPorNombre() {
        Map<String, BigDecimal> valores = new LinkedHashMap<>();
        valores.put(normalizar("Excavacion y estructura"), monto("130893956.20"));
        valores.put(normalizar("Puesta a tierra"), monto("14610216.39"));
        valores.put(normalizar("Mamposteria"), monto("35079730.49"));
        valores.put(normalizar("Revoques"), monto("575107218.88"));
        valores.put(normalizar("Contrapisos y carpetas"), monto("300060703.03"));
        valores.put(normalizar("Cielorrasos"), monto("84903359.56"));
        valores.put(normalizar("Aislaciones e impermeabilizaciones"), monto("368636500.07"));
        valores.put(normalizar("Albanileria"), monto("62693421.93"));
        valores.put(normalizar("Pintura"), monto("397208021.86"));
        valores.put(normalizar("Colocacion pisos"), monto("243354256.55"));
        valores.put(normalizar("Colocacion revestimientos"), monto("111230279.67"));
        valores.put(normalizar("Herrerias"), monto("515512400.50"));
        valores.put(normalizar("Instalacion sanitaria"), monto("706139338.44"));
        valores.put(normalizar("Instalacion electrica"), monto("834997574.19"));
        valores.put(normalizar("Instalacion termomecanica"), monto("211226183.35"));
        valores.put(normalizar("Carpinterias fachada"), monto("383475690.39"));
        valores.put(normalizar("Carpinterias interiores"), monto("627575669.38"));
        valores.put(normalizar("Ascensores"), monto("236850722.91"));
        valores.put(normalizar("Corrientes debiles"), monto("142880098.91"));
        valores.put(normalizar("Artefactos sanitarios y griferias"), monto("181159379.47"));
        valores.put(normalizar("Pisos y revestimientos"), monto("215358879.28"));
        valores.put(normalizar("Muebles de cocina y frentes de placard"), monto("238356551.63"));
        valores.put(normalizar("Marmoleria y granitos"), monto("315023510.40"));
        valores.put(normalizar("Artefactos de iluminacion"), monto("162234796.31"));
        valores.put(normalizar("Senaletica"), monto("58122569.10"));
        return valores;
    }

    private BigDecimal monto(String valor) {
        return new BigDecimal(valor);
    }

    private String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
