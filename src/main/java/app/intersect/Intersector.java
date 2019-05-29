package app.intersect;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.PrecisionModel;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.FilterFactory2;
import org.locationtech.jts.precision.GeometryPrecisionReducer;

public class Intersector {
    private final FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
    
    private final SimpleFeatureCollection col1, col2;
    
    private String the_geom, prefix1, prefix2, name, recalculateLength, recalculateArea;
    
    private SimpleFeatureType sft;
    
    private final List<SimpleFeature> features = Collections.synchronizedList(new ArrayList<>());
    
    public Intersector(SimpleFeatureCollection col1, SimpleFeatureCollection col2) {
        this.col1 = col1;
        this.col2 = col2;
        //prefix1 = col1.getSchema().getTypeName()+"_";
        //prefix2 = col2.getSchema().getTypeName()+"_";
        String string = col1.getSchema().getTypeName()+"_"+col2.getSchema().getTypeName()+"_Intersect";
        setName(string);
    }
    
    public void setPrefixes(String prefix1, String prefix2) {
        this.prefix1 = prefix1;
        this.prefix2 = prefix2;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public void recalculateLength(String recalculateLength) {
        this.recalculateLength = recalculateLength;
    }
    
    public void recalculateArea(String recalculateArea) {
        this.recalculateArea = recalculateArea;
    }
    
    public void intersect() {
        System.out.println("INFO: Intersect has started   " + LocalDateTime.now());
       
        makeSFT();
        doUnthreaded();

        System.out.println("INFO: Intersect has ended   " + LocalDateTime.now());
    }
    
    private void makeSFT() {
        SimpleFeatureType type1 = col1.getSchema();
        SimpleFeatureType type2 = col2.getSchema();

        ExtendedSimpleFeatureTypeBuilder esftb = new ExtendedSimpleFeatureTypeBuilder();
        esftb.init(type1, type2, prefix1, prefix2);
        if (name != null) {
            esftb.setName(name);
        }

        the_geom = type1.getGeometryDescriptor().getLocalName();

        sft = esftb.buildFeatureType();
    }
    
    private void doUnthreaded() {
        SimpleFeatureBuilder sfb = new SimpleFeatureBuilder(sft);
        int i=0;
        Double toBeTruncated;
        Double truncatedDouble;
        //Set precision to 9 decimal places instead of the given 10, solves non-noded intersection problem
        PrecisionModel precisionModel = new PrecisionModel(1000000000);
        SimpleFeatureIterator iter2 = col2.features();
        try {
            while (iter2.hasNext()) {
                SimpleFeature feature2 = iter2.next();
                Geometry geometry2 = (Geometry) feature2.getDefaultGeometry();
                geometry2 = GeometryPrecisionReducer.reduce(geometry2, precisionModel);
                
                SimpleFeatureCollection partCol1 = col1.subCollection(
                        ff.intersects(ff.property(the_geom), ff.literal(geometry2)));
                
                SimpleFeatureIterator iter1 = partCol1.features();
                try {
                    while (iter1.hasNext()) {
                        SimpleFeature feature1 = iter1.next();
                        
                        Geometry intersected = null;
                        Object attRecalcLength = null;
                        Object attRecalcArea = null;

                        if (recalculateLength != null) {
                            attRecalcLength = feature1.getAttribute(recalculateLength);
                        }
                        if (recalculateArea != null) {
                            attRecalcArea = feature1.getAttribute(recalculateArea);
                        }
                        for (Object attribute : feature1.getAttributes()) {
                            if (attribute instanceof Geometry) {
                                Geometry geometry1 = (Geometry) attribute;
                                geometry1 = GeometryPrecisionReducer.reduce(geometry1, precisionModel); //Solve non-noded intersection
                                intersected = geometry1.intersection(geometry2);
                                sfb.add(intersected);
                            }
                            else {
                                if (attribute == attRecalcLength && intersected != null) {
                                    toBeTruncated = intersected.getLength();
                                    truncatedDouble = BigDecimal.valueOf(toBeTruncated)
                                            .setScale(6, RoundingMode.HALF_UP)
                                            .doubleValue();
                                    sfb.add(truncatedDouble);
                                }
                                else if (attribute == attRecalcArea && intersected != null) {
                                    toBeTruncated = intersected.getArea();
                                    truncatedDouble = BigDecimal.valueOf(toBeTruncated)
                                            .setScale(6, RoundingMode.HALF_UP)
                                            .doubleValue();
                                    sfb.add(truncatedDouble);
                                }
                                else {
                                    sfb.add(attribute);
                                }
                            }
                        }
                        
                        for (Object attribute : feature2.getAttributes()) {
                            if (!(attribute instanceof Geometry)) {
                                sfb.add(attribute);
                            }
                        }
                        
                        features.add(sfb.buildFeature(String.valueOf(i++)));
                    }
                } finally {
                    iter1.close();
                }
            }
        } finally {
            iter2.close();
        }
    }
    
    public SimpleFeatureCollection getIntersected() {
        return new ListFeatureCollection(sft, features);
    }  
}
