package com.obra.certificaciones.tablero.service;

import com.obra.certificaciones.certificacion.repository.ItemCertificacionRepository;
import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.repository.ItemOrdenCompraRepository;
import com.obra.certificaciones.tablero.dto.AvancesTableroForm;
import com.obra.certificaciones.tablero.entity.*;
import com.obra.certificaciones.tablero.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class TableroCertificadoService {
    private final TableroCertificadoRepository tableroRepository;
    private final TableroCertificadoItemRepository itemRepository;
    private final ItemCertificacionRepository itemCertificacionRepository;
    private final ItemOrdenCompraRepository ordenItems;
    private final MaterialAsignadoTableroRepository materialesRepository;

    public record MaterialCompra(Long id,Long ocId,String oc,LocalDate fecha,String proveedor,String item,
            String detalle,String unidad,BigDecimal cantidad,BigDecimal precio,BigDecimal importe,
            BigDecimal usado,BigDecimal disponible) {}
    public record AsignacionMaterial(Long materialId,BigDecimal importe) {}
    public record MaterialSeleccionado(Long materialId,String oc,String item,String detalle,BigDecimal importe) {}
    public record ComprasMateriales(List<MaterialCompra> compras,List<MaterialSeleccionado> seleccionados,
            int pagina,int paginas,long cantidad,LocalDate desde,LocalDate hasta,boolean bloqueado) {}

    public ComprasMateriales comprasMateriales(Long id,Long itemId,LocalDate desde,LocalDate hasta,String q,Long ocId,int pagina) {
        var t=obtener(id); var tarea=obtenerItem(id,itemId);
        var inicio=desde==null?t.getFechaDesde():desde; var fin=hasta==null?t.getFechaHasta():hasta;
        validarFechas(inicio,fin);
        var resultado=ordenItems.materialesPeriodo(t.getObra().getId(),inicio,fin,q==null?"":q.trim(),ocId,
                org.springframework.data.domain.PageRequest.of(Math.max(0,pagina),25));
        var usados=new HashMap<Long,BigDecimal>();
        var ids=resultado.getContent().stream().map(ItemOrdenCompra::getId).toList();
        if(!ids.isEmpty()) materialesRepository.usados(t.getObra().getId(),tarea.claveHistorial(),ids)
                .forEach(u->usados.put(u.getMaterialId(),u.getImporte()));
        var compras=resultado.getContent().stream().map(i->{
            var oc=i.getOrdenCompra(); var usado=usados.getOrDefault(i.getId(),BigDecimal.ZERO);
            return new MaterialCompra(i.getId(),oc.getId(),oc.getNumero(),oc.getFecha(),
                    oc.getProveedorEntidad()==null?"Sin proveedor":oc.getProveedorEntidad().getNombre(),i.getItem(),
                    i.getDetalle(),i.getUnidad(),i.getCantidad(),i.getPrecioUnitario(),i.getImporte(),usado,
                    valor(i.getImporte()).subtract(usado).max(BigDecimal.ZERO));
        }).toList();
        var seleccionados=materialesRepository.findByObraIdAndClaveTarea(t.getObra().getId(),tarea.claveHistorial()).stream()
                .map(a->new MaterialSeleccionado(a.getMaterial().getId(),a.getMaterial().getOrdenCompra().getNumero(),
                        a.getMaterial().getItem(),a.getMaterial().getDetalle(),a.getImporte())).toList();
        return new ComprasMateriales(compras,seleccionados,resultado.getNumber(),resultado.getTotalPages(),
                resultado.getTotalElements(),inicio,fin,tarea.materialesCerrados()||t.isCerrado()||tieneSiguiente(id));
    }

    @Transactional
    public BigDecimal asignarMateriales(Long id,Long itemId,List<AsignacionMaterial> datos) {
        var t=editable(id); var tarea=obtenerItem(id,itemId);
        if(tarea.materialesCerrados()) throw new IllegalArgumentException("Los materiales están bloqueados porque la tarea ya fue certificada.");
        if(datos==null || datos.size()>1000) throw new IllegalArgumentException("La selección de materiales es inválida.");
        var importes=new TreeMap<Long,BigDecimal>();
        for(var d:datos) {
            if(d.materialId()==null || d.importe()==null || d.importe().signum()<=0 || d.importe().stripTrailingZeros().scale()>2
                    || importes.putIfAbsent(d.materialId(),d.importe())!=null)
                throw new IllegalArgumentException("Revisá los importes: deben ser positivos, con hasta dos decimales y sin ítems repetidos.");
        }
        var actuales=materialesRepository.findByObraIdAndClaveTarea(t.getObra().getId(),tarea.claveHistorial());
        var ids=new TreeSet<>(importes.keySet()); actuales.forEach(a->ids.add(a.getMaterial().getId()));
        var materiales=ids.isEmpty()?List.<ItemOrdenCompra>of():ordenItems.bloquearMateriales(new ArrayList<>(ids));
        var usados=new HashMap<Long,BigDecimal>();
        if(!ids.isEmpty()) materialesRepository.bloquearUsados(t.getObra().getId(),tarea.claveHistorial(),new ArrayList<>(ids))
                .forEach(a->usados.merge(a.getMaterial().getId(),a.getImporte(),BigDecimal::add));
        var encontrados=new HashMap<Long,ItemOrdenCompra>(); materiales.forEach(i->encontrados.put(i.getId(),i));
        for(var entry:importes.entrySet()) {
            var material=encontrados.get(entry.getKey());
            if(material==null || !material.getOrdenCompra().getObra().getId().equals(t.getObra().getId()) || tipo(material)!=CategoriaItem.MATERIAL)
                throw new IllegalArgumentException("Un material no pertenece a esta obra o no es una compra de materiales.");
            var disponible=valor(material.getImporte()).subtract(usados.getOrDefault(material.getId(),BigDecimal.ZERO));
            if(entry.getValue().compareTo(disponible)>0)
                throw new IllegalArgumentException("El importe de "+material.getItem()+" supera el disponible para asignar ($ "+disponible+").");
        }
        var pendientes=new TreeMap<>(importes);
        for(var a:actuales) {
            var importe=pendientes.remove(a.getMaterial().getId());
            if(importe==null) materialesRepository.delete(a); else a.setImporte(importe);
        }
        pendientes.forEach((materialId,importe)->{
            var a=new MaterialAsignadoTablero(); a.setObraId(t.getObra().getId()); a.setClaveTarea(tarea.claveHistorial());
            a.setMaterial(encontrados.get(materialId)); a.setImporte(importe); materialesRepository.save(a);
        });
        var total=importes.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        tarea.setMaterialesAsignados(total); tarea.setMaterialesConfirmados(true);
        tarea.setTotalTareaInformado(null); tarea.setMontoActualInformado(null); tarea.setMontoAcumuladoInformado(null);
        return total;
    }

    public List<TableroCertificado> listar(Obra obra) { return tableroRepository.findByObraIdOrderByFechaHastaDescIdDesc(obra.getId()); }
    public TableroCertificado obtener(Long id) { return tableroRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("No existe el certificado general.")); }
    public void validarObra(Long id, Long obraId) {
        if (!obtener(id).getObra().getId().equals(obraId)) throw new IllegalArgumentException("El certificado no pertenece a la obra activa.");
    }
    public boolean tieneSiguiente(Long id) { return tableroRepository.existsByAnteriorId(id); }

    @Transactional
    public TableroCertificado crear(Obra obra, String nombre, LocalDate desde, LocalDate hasta) {
        var mes=YearMonth.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        var t=new TableroCertificado(); t.setObra(obra); t.setNumero(siguienteNumero(obra));
        t.setFechaDesde(desde==null?mes.atDay(1):desde); t.setFechaHasta(hasta==null?YearMonth.from(t.getFechaDesde()).atEndOfMonth():hasta);
        validarFechas(t.getFechaDesde(),t.getFechaHasta());
        t.setNombre(StringUtils.hasText(nombre)?nombre.trim():"Certificado general " + t.getNumero());
        t.setCliente(obra.getCliente()); t.setEmpresa("SIMENDE");
        return tableroRepository.save(t);
    }
    @Transactional
    public TableroCertificado guardarDatos(TableroCertificado datos) {
        var t=editable(datos.getId()); validarFechas(datos.getFechaDesde(),datos.getFechaHasta());
        if (t.getAnteriorId()!=null && !datos.getFechaDesde().isAfter(obtener(t.getAnteriorId()).getFechaHasta()))
            throw new IllegalArgumentException("El nuevo período debe comenzar después del certificado anterior.");
        if (!StringUtils.hasText(datos.getNombre())) throw new IllegalArgumentException("Ingresá un nombre para el certificado.");
        if (datos.getNumero()!=null && (datos.getNumero()<1 || listar(t.getObra()).stream().anyMatch(o->!o.getId().equals(t.getId()) && datos.getNumero().equals(o.getNumero()))))
            throw new IllegalArgumentException("El número de certificado debe ser positivo y no estar repetido en esta obra.");
        t.setNumero(datos.getNumero()); t.setNombre(datos.getNombre().trim()); t.setCliente(datos.getCliente()); t.setEmpresa(datos.getEmpresa());
        t.setFechaDesde(datos.getFechaDesde()); t.setFechaHasta(datos.getFechaHasta()); t.setObservacion(datos.getObservacion());
        return t;
    }
    @Transactional
    public TableroCertificado continuar(Long id) {
        var anterior=bloquear(id);
        if (!anterior.isCerrado()) throw new IllegalArgumentException("Cerrá el certificado antes de continuar al siguiente período.");
        if (tieneSiguiente(id)) throw new IllegalArgumentException("Este certificado ya tiene un período siguiente. Abrilo desde el historial.");
        var inicio=anterior.getFechaHasta().plusDays(1);
        var nuevo=crear(anterior.getObra(),null,inicio,YearMonth.from(inicio).atEndOfMonth());
        nuevo.setAnteriorId(id); nuevo.setCliente(anterior.getCliente()); nuevo.setEmpresa(anterior.getEmpresa());
        for (var fila:anterior.getItems()) {
            var copia=new TableroCertificadoItem();
            BeanUtils.copyProperties(fila,copia,"id","tablero","avanceCertificadoPorcentaje","avanceAnteriorPorcentaje","montoAnteriorFijo","materialesAdicionalesEtapa","montoActualInformado","montoAcumuladoInformado");
            copia.setClaveContinuidad(fila.claveHistorial());
            copia.setAvanceAnteriorPorcentaje(fila.isRecurrente()?BigDecimal.ZERO:fila.avanceAcumuladoPorcentaje()); copia.setMontoAnteriorFijo(fila.montoAcumulado());
            copia.setAvanceCertificadoPorcentaje(BigDecimal.ZERO); copia.setMaterialesAdicionalesEtapa(BigDecimal.ZERO);
            nuevo.agregarItem(copia);
        }
        nuevo.setAjusteAnterior(anterior.montoAcumulado().subtract(nuevo.montoAnterior()));
        return tableroRepository.save(nuevo);
    }
    @Transactional
    public void cerrar(Long id) {
        var t=editable(id);
        if (t.getItems().isEmpty()) throw new IllegalArgumentException("Agregá al menos una tarea antes de cerrar.");
        validarFechas(t.getFechaDesde(),t.getFechaHasta());
        for (var i:t.getItems()) validarFila(i);
        if (t.montoCertificado().signum()<=0) throw new IllegalArgumentException("El certificado debe tener un importe actual mayor a cero para cerrarlo.");
        t.setEmitido(true);
    }
    @Transactional
    public void reabrir(Long id) {
        var t=bloquear(id);
        if (tieneSiguiente(id)) throw new IllegalArgumentException("Ya existe un certificado posterior; el anterior se conserva como historial.");
        t.setEmitido(false);
    }
    @Transactional
    public TableroCertificadoItem crearItemManual(Long id) {
        var t=editable(id); var i=new TableroCertificadoItem();
        i.setClaveContinuidad(UUID.randomUUID().toString()); i.setDescripcionTarea("Nueva tarea"); i.setUnidad("gl");
        i.setCantidad(BigDecimal.ONE); i.setOrdenFila(siguienteOrden(t));
        t.agregarItem(i); return itemRepository.saveAndFlush(i);
    }
    @Transactional
    public TableroCertificadoItem crearGrupo(Long id, String nombre) {
        var i=crearItemManual(id); i.setGrupo(true); i.setGrupoNombre(nombre); i.setDescripcionTarea(StringUtils.hasText(nombre)?nombre:"Partida global"); i.setSubtotalManual(BigDecimal.ZERO); return i;
    }
    public TableroCertificadoItem obtenerItem(Long id,Long itemId) {
        return obtener(id).getItems().stream().filter(i->i.getId().equals(itemId)).findFirst().orElseThrow(()->new IllegalArgumentException("La tarea no pertenece a este certificado."));
    }
    @Transactional
    public TableroCertificadoItem guardarItem(Long id,Long itemId,TableroCertificadoItem d) {
        editable(id); var i=obtenerItem(id,itemId);
        var asignados=materialesRepository.findByObraIdAndClaveTarea(i.getTablero().getObra().getId(),i.claveHistorial());
        if(!asignados.isEmpty() && !igual(d.getMaterialesAsignados(),asignados.stream().map(MaterialAsignadoTablero::getImporte).reduce(BigDecimal.ZERO,BigDecimal::add)))
            throw new IllegalArgumentException("Modificá el importe de materiales desde la selección de compras para conservar sus vínculos.");
        if (i.materialesCerrados() && (!igual(i.getMaterialesAsignados(),d.getMaterialesAsignados())
                || !igual(i.getMaterialesSuministradosEmpresa(),d.getMaterialesSuministradosEmpresa())
                || !igual(i.getMaterialesAdicionalesEtapa(),d.getMaterialesAdicionalesEtapa())
                || !igual(i.getSubtotalManual(),d.getSubtotalManual())))
            throw new IllegalArgumentException("Esta tarea ya fue certificada. Sus materiales y costo base informado están bloqueados.");
        if (i.materialesCerrados()) i.setMaterialesBloqueados(true);
        else i.setMaterialesConfirmados(Boolean.TRUE.equals(d.getMaterialesConfirmados()));
        if (!StringUtils.hasText(d.getDescripcionTarea())) throw new IllegalArgumentException("La descripción de la tarea es obligatoria.");
        // Los avances anteriores y el vínculo de origen nunca se toman del formulario.
        if (i.getMontoAnteriorFijo()==null) i.setMontoAnteriorFijo(i.montoAnterior());
        boolean cambiaBase = !igual(i.getCostoManoObra(),d.getCostoManoObra()) || !igual(i.getMaterialesAsignados(),d.getMaterialesAsignados()) || !igual(i.getServicios(),d.getServicios()) || !igual(i.getMaterialesSuministradosEmpresa(),d.getMaterialesSuministradosEmpresa()) || !igual(i.getSubtotalManual(),d.getSubtotalManual()) || !igual(i.getCostoEstructuralPorcentaje(),d.getCostoEstructuralPorcentaje()) || !igual(i.getBeneficioEmpresarialPorcentaje(),d.getBeneficioEmpresarialPorcentaje());
        if(cambiaBase) i.setTotalTareaInformado(null);
        if(cambiaBase || !igual(i.getMaterialesAdicionalesEtapa(),d.getMaterialesAdicionalesEtapa())) { i.setMontoActualInformado(null); i.setMontoAcumuladoInformado(null); }
        i.setItemCodigo(d.getItemCodigo()); i.setCodigoTarea(d.getCodigoTarea()); i.setContratista(d.getContratista()); i.setRubro(d.getRubro());
        i.setDescripcionTarea(d.getDescripcionTarea()); i.setUnidad(d.getUnidad()); i.setCantidad(valor(d.getCantidad())); i.setPrecioUnitario(valor(d.getPrecioUnitario()));
        i.setCostoManoObra(valor(d.getCostoManoObra())); i.setMaterialesAsignados(valor(d.getMaterialesAsignados())); i.setServicios(valor(d.getServicios()));
        i.setMaterialesSuministradosEmpresa(valor(d.getMaterialesSuministradosEmpresa())); i.setMaterialesAdicionalesEtapa(valor(d.getMaterialesAdicionalesEtapa()));
        i.setSubtotalManual(d.getSubtotalManual()); i.setCostoEstructuralPorcentaje(valor(d.getCostoEstructuralPorcentaje())); i.setBeneficioEmpresarialPorcentaje(valor(d.getBeneficioEmpresarialPorcentaje()));
        i.setObservacion(d.getObservacion()); validarFila(i); return i;
    }
    @Transactional
    public TableroCertificadoItem guardarItemConCompras(Long id,Long itemId,TableroCertificadoItem datos,
            List<Long> ids,List<BigDecimal> importes) {
        var seleccion=ids==null?List.<Long>of():ids;
        var montos=importes==null?List.<BigDecimal>of():importes;
        if(seleccion.size()!=montos.size())throw new IllegalArgumentException("La selección de compras está incompleta.");
        var filas=new ArrayList<AsignacionMaterial>();
        for(int n=0;n<seleccion.size();n++)filas.add(new AsignacionMaterial(seleccion.get(n),montos.get(n)));
        datos.setMaterialesAsignados(asignarMateriales(id,itemId,filas));
        materialesRepository.flush();
        datos.setMaterialesConfirmados(true);
        return guardarItem(id,itemId,datos);
    }

    @Transactional
    public void guardarAvances(Long id,AvancesTableroForm f) {
        var t=editable(id); Map<Long,TableroCertificadoItem> filas=new HashMap<>(); t.getItems().forEach(i->filas.put(i.getId(),i));
        var vistos=new HashSet<Long>();
        for (var fila:f.getFilas()) {
            var i=filas.get(fila.getId()); if (i==null || !vistos.add(fila.getId())) throw new IllegalArgumentException("Hay una tarea inválida o repetida en la carga.");
            validarAvance(i,fila.getPorcentaje());
            if (valor(fila.getPorcentaje()).signum()>0 && !i.materialesPreparados())
                throw new IllegalArgumentException("Revisá y confirmá los materiales de la tarea " + i.getDescripcionTarea() + " antes de certificarla.");
        }
        for (var fila:f.getFilas()) {
            var i=filas.get(fila.getId());
            if (i.materialesCerrados() || valor(fila.getPorcentaje()).signum()>0) i.setMaterialesBloqueados(true);
            if(!igual(i.getAvanceCertificadoPorcentaje(),fila.getPorcentaje())) { i.setMontoActualInformado(null); i.setMontoAcumuladoInformado(null); }
            i.setAvanceCertificadoPorcentaje(valor(fila.getPorcentaje()));
        }
    }
    @Transactional
    public void eliminarItem(Long id,Long itemId) {
        var t=editable(id); var i=obtenerItem(id,itemId);
        if (valor(i.getAvanceAnteriorPorcentaje()).signum()>0 || i.montoAnterior().signum()!=0)
            throw new IllegalArgumentException("Esta tarea tiene importes anteriores. Dejala con avance actual cero para conservar el acumulado.");
        liberarMaterialesSiUltima(t,i);
        t.getItems().remove(i);
    }
    @Transactional
    public void eliminarTablero(Long id) { var t=editable(id); if(tieneSiguiente(id)) throw new IllegalArgumentException("El certificado tiene un período siguiente."); t.getItems().forEach(i->liberarMaterialesSiUltima(t,i)); tableroRepository.delete(t); }

    private void liberarMaterialesSiUltima(TableroCertificado t,TableroCertificadoItem i) {
        if(itemRepository.countByTableroObraIdAndClaveContinuidad(t.getObra().getId(),i.claveHistorial())<=1)
            materialesRepository.deleteAll(materialesRepository.findByObraIdAndClaveTarea(t.getObra().getId(),i.claveHistorial()));
    }

    public List<ItemOrdenCompra> candidatos(Long id) {
        var t=obtener(id); Set<Long> incluidos=new HashSet<>(); t.getItems().stream().filter(i->i.getItemOrdenCompra()!=null).forEach(i->incluidos.add(i.getItemOrdenCompra().getId()));
        return ordenItems.findByOrdenCompraObraIdOrderByIdAsc(t.getObra().getId()).stream()
                .filter(i->tipo(i)!=CategoriaItem.MATERIAL && i.getOrdenCompra().usaSeguimientoCertificacion() && !incluidos.contains(i.getId())).toList();
    }
    @Transactional
    public int agregarItemsOc(Long id,List<Long> ids) {
        var t=editable(id); var disponibles=new HashMap<Long,ItemOrdenCompra>(); candidatos(id).forEach(i->disponibles.put(i.getId(),i));
        var unicos=new LinkedHashSet<>(ids); for (var itemId:unicos) if(!disponibles.containsKey(itemId)) throw new IllegalArgumentException("Una tarea seleccionada no está disponible en esta obra o ya fue agregada.");
        int orden=siguienteOrden(t);
        for (var itemId:unicos) { var original=disponibles.get(itemId); var i=desdeOc(original,orden++); t.agregarItem(i); }
        return unicos.size();
    }
    @Transactional
    public int importarItemsCertificadosPeriodo(Long id) {
        var t=editable(id); var ids=itemCertificacionRepository.buscarCertificablesPeriodo(t.getObra().getId(),t.getFechaDesde(),t.getFechaHasta()).stream().map(i->i.getItemOrdenCompra().getId()).collect(java.util.stream.Collectors.toSet());
        var elegibles=candidatos(id).stream().map(ItemOrdenCompra::getId).filter(ids::contains).toList();
        return agregarItemsOc(id,elegibles);
    }
    private TableroCertificadoItem desdeOc(ItemOrdenCompra item,int orden) {
        var i=new TableroCertificadoItem(); i.setClaveContinuidad(UUID.randomUUID().toString()); i.setItemOrdenCompra(item); i.setOrdenFila(orden);
        i.setContratista(item.getOrdenCompra().getProveedorEntidad()==null?null:item.getOrdenCompra().getProveedorEntidad().getNombre());
        i.setRubro(item.getRubroEntidad()==null?item.getRubro():item.getRubroEntidad().getNombreCompleto()); i.setCodigoTarea(item.getItem());
        i.setItemCodigo(item.getOrdenCompra().getNumero()+" / "+item.getItem()); i.setDescripcionTarea(item.getDetalle()); i.setUnidad(item.getUnidad()); i.setCantidad(valor(item.getCantidad())); i.setPrecioUnitario(valor(item.getPrecioUnitario()));
        if (tipo(item)==CategoriaItem.MANO_OBRA) i.setCostoManoObra(valor(item.getImporte())); else i.setServicios(valor(item.getImporte()));
        // Se incorpora el presupuesto; el usuario define el avance de cobro, que empieza en cero.
        return i;
    }
    public Historial historial(Obra obra) {
        var certificados=new ArrayList<>(listar(obra)); certificados.sort(Comparator.comparing(TableroCertificado::getFechaHasta).thenComparing(TableroCertificado::getId));
        Map<String,TableroCertificadoItem> ultimos=new LinkedHashMap<>();
        for(var t:certificados) for(var i:t.getItems()) ultimos.put(i.claveHistorial(),i);
        var mapas=certificados.stream().map(t->{ Map<String,TableroCertificadoItem> m=new HashMap<>(); t.getItems().forEach(i->m.put(i.claveHistorial(),i)); return m; }).toList();
        var filas=new ArrayList<FilaHistorial>();
        for(var e:ultimos.entrySet()) {
            var celdas=new ArrayList<CeldaHistorial>();
            for(var mapa:mapas) {
                var i=Optional.ofNullable(mapa.get(e.getKey()));
                celdas.add(i.map(f->new CeldaHistorial(f.getAvanceCertificadoPorcentaje(),f.avanceAcumuladoPorcentaje(),f.montoCertificado(),f.montoAcumulado())).orElse(null));
            }
            var i=e.getValue(); filas.add(new FilaHistorial(i.getItemCodigo(),i.getDescripcionTarea(),i.getRubro(),i.totalTarea(),celdas));
        }
        return new Historial(certificados,filas);
    }
    public TotalesTablero totales(Long id) {
        var t=obtener(id); var items=t.getItems();
        return new TotalesTablero(items.size(),sum(items,TableroCertificadoItem::costoSubtotal),sum(items,TableroCertificadoItem::costoEstructuralMonto),sum(items,TableroCertificadoItem::beneficioEmpresarialMonto),t.totalTarea(),t.montoCertificado(),t.montoAnterior(),t.montoAcumulado());
    }
    private BigDecimal sum(List<TableroCertificadoItem> items,java.util.function.Function<TableroCertificadoItem,BigDecimal> fn) { return items.stream().map(fn).reduce(BigDecimal.ZERO,BigDecimal::add); }
    private TableroCertificado bloquear(Long id) { return tableroRepository.bloquear(id).orElseThrow(()->new EntityNotFoundException("No existe el certificado general.")); }
    private TableroCertificado editable(Long id) { var t=bloquear(id); if(t.isCerrado()) throw new IllegalArgumentException("El certificado está cerrado. Reabrilo antes de modificarlo."); if(tieneSiguiente(id)) throw new IllegalArgumentException("Este certificado ya tiene un período posterior y se conserva como historial."); return t; }
    private void validarFila(TableroCertificadoItem i) {
        if(!StringUtils.hasText(i.getDescripcionTarea())) throw new IllegalArgumentException("Todas las tareas deben tener descripción.");
        if(valor(i.getCantidad()).signum()<0 || valor(i.getPrecioUnitario()).signum()<0 || valor(i.getCostoManoObra()).signum()<0 || valor(i.getCostoEstructuralPorcentaje()).signum()<0 || valor(i.getBeneficioEmpresarialPorcentaje()).signum()<0 || i.costoSubtotal().signum()<0)
            throw new IllegalArgumentException("Revisá cantidades, precios y costos: el costo base y los porcentajes no pueden ser negativos.");
        validarAvance(i,i.getAvanceCertificadoPorcentaje());
    }
    private void validarAvance(TableroCertificadoItem i,BigDecimal p) {
        if(p==null || p.signum()<0 || p.stripTrailingZeros().scale()>2 || p.compareTo(i.porcentajeDisponible())>0)
            throw new IllegalArgumentException("La tarea «"+i.getDescripcionTarea()+"» debe tener un avance actual entre 0 y "+i.porcentajeDisponible().stripTrailingZeros().toPlainString()+" % (hasta dos decimales).");
    }
    private void validarFechas(LocalDate desde,LocalDate hasta) { if(desde==null || hasta==null || hasta.isBefore(desde)) throw new IllegalArgumentException("Indicá un período válido: la fecha final debe ser igual o posterior a la inicial."); }
    private int siguienteNumero(Obra obra) { return listar(obra).stream().map(TableroCertificado::getNumero).filter(Objects::nonNull).max(Integer::compareTo).orElse(0)+1; }
    private int siguienteOrden(TableroCertificado t) { return t.getItems().stream().map(TableroCertificadoItem::getOrdenFila).filter(Objects::nonNull).max(Integer::compareTo).orElse(0)+1; }
    private CategoriaItem tipo(ItemOrdenCompra i) { return i.getCategoriaEntidad()==null?i.getCategoria():i.getCategoriaEntidad().getTipo(); }
    private boolean igual(BigDecimal a,BigDecimal b) { return (a==null && b==null) || (a!=null && b!=null && a.compareTo(b)==0); }
    private BigDecimal valor(BigDecimal n) { return n==null?BigDecimal.ZERO:n; }
    public record TotalesTablero(int cantidadItems,BigDecimal subtotal,BigDecimal estructura,BigDecimal beneficio,BigDecimal total,BigDecimal certificado,BigDecimal anterior,BigDecimal acumulado) {}
    public record CeldaHistorial(BigDecimal actual,BigDecimal acumulado,BigDecimal monto,BigDecimal montoAcumulado) {}
    public record FilaHistorial(String codigo,String descripcion,String rubro,BigDecimal total,List<CeldaHistorial> celdas) {}
    public record Historial(List<TableroCertificado> certificados,List<FilaHistorial> filas) {}
}
