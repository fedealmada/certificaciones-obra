package com.obra.certificaciones.tablero.service;

import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.repository.OrdenCompraRepository;
import com.obra.certificaciones.tablero.entity.TableroCertificado;
import com.obra.certificaciones.tablero.entity.TableroCertificadoItem;
import com.obra.certificaciones.tablero.repository.TableroCertificadoItemRepository;
import com.obra.certificaciones.tablero.repository.TableroCertificadoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TableroCertificadoService {
    private final TableroCertificadoRepository tableroRepository;
    private final TableroCertificadoItemRepository itemRepository;
    private final OrdenCompraRepository ordenCompraRepository;

    @Transactional(readOnly = true)
    public List<TableroCertificado> listar(Obra obra) {
        return tableroRepository.findByObraIdOrderByFechaHastaDescIdDesc(obra.getId());
    }

    @Transactional(readOnly = true)
    public TableroCertificado obtener(Long id) {
        return tableroRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el tablero de certificado " + id));
    }

    @Transactional
    public TableroCertificado crear(Obra obra, String nombre, LocalDate fechaDesde, LocalDate fechaHasta) {
        YearMonth periodo = YearMonth.now();
        TableroCertificado tablero = new TableroCertificado();
        tablero.setObra(obra);
        tablero.setNombre(StringUtils.hasText(nombre) ? nombre.trim() : "Tablero " + periodo);
        tablero.setFechaDesde(fechaDesde == null ? periodo.atDay(1) : fechaDesde);
        tablero.setFechaHasta(fechaHasta == null ? periodo.atEndOfMonth() : fechaHasta);
        return tableroRepository.save(tablero);
    }

    @Transactional
    public TableroCertificado guardarDatos(TableroCertificado datos) {
        TableroCertificado tablero = obtener(datos.getId());
        tablero.setNombre(datos.getNombre());
        tablero.setFechaDesde(datos.getFechaDesde());
        tablero.setFechaHasta(datos.getFechaHasta());
        tablero.setObservacion(datos.getObservacion());
        return tableroRepository.save(tablero);
    }

    @Transactional
    public int importarItemsDesdeOrdenes(Long tableroId) {
        TableroCertificado tablero = obtener(tableroId);
        List<OrdenCompra> ordenes = ordenCompraRepository.buscarConFiltros(tablero.getObra().getId(), null, null, null);
        int agregados = 0;
        int orden = siguienteOrden(tableroId);
        for (OrdenCompra ordenCompra : ordenes) {
            for (ItemOrdenCompra item : ordenCompra.getItems()) {
                if (item.getId() == null || itemRepository.existsByTableroIdAndItemOrdenCompraId(tableroId, item.getId())) {
                    continue;
                }
                TableroCertificadoItem fila = desdeItemOrdenCompra(item, orden++);
                tablero.agregarItem(fila);
                agregados++;
            }
        }
        tableroRepository.save(tablero);
        return agregados;
    }

    @Transactional
    public TableroCertificadoItem crearGrupo(Long tableroId, String nombreGrupo) {
        TableroCertificado tablero = obtener(tableroId);
        TableroCertificadoItem item = new TableroCertificadoItem();
        item.setGrupo(true);
        item.setGrupoNombre(StringUtils.hasText(nombreGrupo) ? nombreGrupo.trim() : "Nuevo grupo");
        item.setDescripcionTarea(item.getGrupoNombre());
        item.setUnidad("gl");
        item.setCantidad(BigDecimal.ONE);
        item.setPrecioUnitario(BigDecimal.ZERO);
        item.setSubtotalManual(BigDecimal.ZERO);
        item.setCostoEstructuralPorcentaje(BigDecimal.valueOf(20));
        item.setBeneficioEmpresarialPorcentaje(BigDecimal.valueOf(25));
        item.setOrdenFila(siguienteOrden(tableroId));
        tablero.agregarItem(item);
        tableroRepository.save(tablero);
        return item;
    }

    @Transactional
    public TableroCertificadoItem crearItemManual(Long tableroId) {
        TableroCertificado tablero = obtener(tableroId);
        TableroCertificadoItem item = new TableroCertificadoItem();
        item.setDescripcionTarea("Nuevo item");
        item.setUnidad("un");
        item.setCantidad(BigDecimal.ONE);
        item.setPrecioUnitario(BigDecimal.ZERO);
        item.setCostoEstructuralPorcentaje(BigDecimal.valueOf(20));
        item.setBeneficioEmpresarialPorcentaje(BigDecimal.valueOf(25));
        item.setOrdenFila(siguienteOrden(tableroId));
        tablero.agregarItem(item);
        tableroRepository.save(tablero);
        return item;
    }

    @Transactional
    public TableroCertificadoItem guardarItem(Long itemId, TableroCertificadoItem datos) {
        TableroCertificadoItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el item del tablero " + itemId));
        item.setGrupoNombre(datos.getGrupoNombre());
        item.setContratista(datos.getContratista());
        item.setRubro(datos.getRubro());
        item.setCodigoTarea(datos.getCodigoTarea());
        item.setItemCodigo(datos.getItemCodigo());
        item.setDescripcionTarea(datos.getDescripcionTarea());
        item.setUnidad(datos.getUnidad());
        item.setCantidad(valor(datos.getCantidad()));
        item.setPrecioUnitario(valor(datos.getPrecioUnitario()));
        item.setCostoManoObra(valor(datos.getCostoManoObra()));
        item.setMaterialesAsignados(valor(datos.getMaterialesAsignados()));
        item.setServicios(valor(datos.getServicios()));
        item.setMaterialesSuministradosEmpresa(valor(datos.getMaterialesSuministradosEmpresa()));
        item.setSubtotalManual(datos.getSubtotalManual());
        item.setCostoEstructuralPorcentaje(valor(datos.getCostoEstructuralPorcentaje()));
        item.setBeneficioEmpresarialPorcentaje(valor(datos.getBeneficioEmpresarialPorcentaje()));
        item.setAvanceCertificadoPorcentaje(valor(datos.getAvanceCertificadoPorcentaje()));
        item.setObservacion(datos.getObservacion());
        if (!item.isGrupo() && item.getCostoManoObra().compareTo(BigDecimal.ZERO) == 0) {
            item.recalcularManoObra();
        }
        return itemRepository.save(item);
    }

    @Transactional
    public void eliminarItem(Long itemId) {
        itemRepository.deleteById(itemId);
    }

    @Transactional(readOnly = true)
    public TotalesTablero totales(Long tableroId) {
        List<TableroCertificadoItem> items = itemRepository.findByTableroIdOrderByOrdenFilaAscIdAsc(tableroId);
        BigDecimal subtotal = items.stream().map(TableroCertificadoItem::costoSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estructura = items.stream().map(TableroCertificadoItem::costoEstructuralMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal beneficio = items.stream().map(TableroCertificadoItem::beneficioEmpresarialMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = items.stream().map(TableroCertificadoItem::totalTarea).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal certificado = items.stream().map(TableroCertificadoItem::montoCertificado).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TotalesTablero(items.size(), subtotal, estructura, beneficio, total, certificado);
    }

    private TableroCertificadoItem desdeItemOrdenCompra(ItemOrdenCompra item, int orden) {
        TableroCertificadoItem fila = new TableroCertificadoItem();
        fila.setItemOrdenCompra(item);
        fila.setOrdenFila(orden);
        fila.setContratista(item.getOrdenCompra().getProveedorEntidad() == null ? null : item.getOrdenCompra().getProveedorEntidad().getNombre());
        fila.setRubro(item.getRubroEntidad() == null ? item.getRubro() : item.getRubroEntidad().getNombreCompleto());
        fila.setCodigoTarea(item.getItem());
        fila.setItemCodigo(item.getItem());
        fila.setDescripcionTarea(item.getDetalle());
        fila.setUnidad(item.getUnidad());
        fila.setCantidad(valor(item.getCantidad()));
        fila.setPrecioUnitario(valor(item.getPrecioUnitario()));
        BigDecimal importe = valor(item.getImporte());
        if (item.getCategoria() == CategoriaItem.MATERIAL) {
            fila.setMaterialesAsignados(importe);
        } else if (item.getCategoria() == CategoriaItem.MANO_OBRA) {
            fila.setCostoManoObra(importe);
        } else {
            fila.setServicios(importe);
        }
        fila.setCostoEstructuralPorcentaje(BigDecimal.valueOf(20));
        fila.setBeneficioEmpresarialPorcentaje(BigDecimal.valueOf(25));
        return fila;
    }

    private int siguienteOrden(Long tableroId) {
        return itemRepository.findByTableroIdOrderByOrdenFilaAscIdAsc(tableroId).stream()
                .map(TableroCertificadoItem::getOrdenFila)
                .max(Comparator.nullsFirst(Integer::compareTo))
                .orElse(0) + 1;
    }

    private BigDecimal valor(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    public record TotalesTablero(
            int cantidadItems,
            BigDecimal subtotal,
            BigDecimal estructura,
            BigDecimal beneficio,
            BigDecimal total,
            BigDecimal certificado
    ) {
    }
}
