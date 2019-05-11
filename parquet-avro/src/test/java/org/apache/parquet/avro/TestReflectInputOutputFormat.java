/* 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.parquet.avro;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.avro.Schema;
import org.apache.avro.reflect.Nullable;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.Union;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.parquet.column.ColumnReader;
import org.apache.parquet.filter.ColumnPredicates;
import org.apache.parquet.filter.ColumnRecordFilter;
import org.apache.parquet.filter.RecordFilter;
import org.apache.parquet.filter.UnboundRecordFilter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Thread.sleep;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class TestReflectInputOutputFormat {
  private static final Logger LOG = LoggerFactory.getLogger(TestReflectInputOutputFormat.class);


  static class Service {
    private long date;
    private String mechanic;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Service service = (Service) o;

      if (date != service.date) return false;
      return mechanic.equals(service.mechanic);
    }

    @Override
    public int hashCode() {
      int result = (int) (date ^ (date >>> 32));
      result = 31 * result + mechanic.hashCode();
      return result;
    }
  }

  public enum EngineType {
    DIESEL, PETROL, ELECTRIC
  }

  static class Engine {
    private EngineType type;
    private float capacity;
    private boolean hasTurboCharger;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Engine engine = (Engine) o;

      if (Float.compare(engine.capacity, capacity) != 0) return false;
      if (hasTurboCharger != engine.hasTurboCharger) return false;
      return type == engine.type;
    }

    @Override
    public int hashCode() {
      int result = type.hashCode();
      result = 31 * result + (capacity != +0.0f ? Float.floatToIntBits(capacity) : 0);
      result = 31 * result + (hasTurboCharger ? 1 : 0);
      return result;
    }
  }

  static class Stereo extends Extra {
    private String make;
    private int speakers;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Stereo stereo = (Stereo) o;

      if (speakers != stereo.speakers) return false;
      return make.equals(stereo.make);
    }

    @Override
    public int hashCode() {
      int result = make.hashCode();
      result = 31 * result + speakers;
      return result;
    }
  }

  static class LeatherTrim extends Extra {
    private String colour;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      LeatherTrim that = (LeatherTrim) o;

      return colour.equals(that.colour);
    }

    @Override
    public int hashCode() {
      return colour.hashCode();
    }
  }

  @Union({Void.class, Stereo.class, LeatherTrim.class})
  static class Extra {}

  static class Car {
    private long year;
    private String registration;
    private String make;
    private String model;
    private byte[] vin;
    private int doors;
    private Engine engine;
    private Extra optionalExtra = null;
    @Nullable
    private List<Service> serviceHistory = null;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Car car = (Car) o;

      if (doors != car.doors) return false;
      if (year != car.year) return false;
      if (!engine.equals(car.engine)) return false;
      if (!make.equals(car.make)) return false;
      if (!model.equals(car.model)) return false;
      if (!Objects.equals(optionalExtra, car.optionalExtra))
        return false;
      if (!registration.equals(car.registration)) return false;
      if (!Objects.equals(serviceHistory, car.serviceHistory))
        return false;
      return Arrays.equals(vin, car.vin);
    }

    @Override
    public int hashCode() {
      int result = (int) (year ^ (year >>> 32));
      result = 31 * result + registration.hashCode();
      result = 31 * result + make.hashCode();
      result = 31 * result + model.hashCode();
      result = 31 * result + Arrays.hashCode(vin);
      result = 31 * result + doors;
      result = 31 * result + engine.hashCode();
      result = 31 * result + (optionalExtra != null ? optionalExtra.hashCode() : 0);
      result = 31 * result + (serviceHistory != null ? serviceHistory.hashCode() : 0);
      return result;
    }
  }

  static class ShortCar {
    @Nullable
    private final String make = null;
    private Engine engine;
    private long year;
    private byte[] vin;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ShortCar shortCar = (ShortCar) o;

      if (year != shortCar.year) return false;
      if (!engine.equals(shortCar.engine)) return false;
      if (!Objects.equals(make, shortCar.make))
        return false;
      return Arrays.equals(vin, shortCar.vin);
    }

    @Override
    public int hashCode() {
      int result = make != null ? make.hashCode() : 0;
      result = 31 * result + engine.hashCode();
      result = 31 * result + (int) (year ^ (year >>> 32));
      result = 31 * result + Arrays.hashCode(vin);
      return result;
    }
  }

  private static final Schema CAR_SCHEMA = ReflectData.get()//AllowNulls.INSTANCE
      .getSchema(Car.class);
  private static final Schema SHORT_CAR_SCHEMA = ReflectData.get()//AllowNulls.INSTANCE
      .getSchema(ShortCar.class);

  private static Car nextRecord(int i) {
    Car car = new Car();
    car.doors = 2;
    car.make = "Tesla";
    car.model = String.format("Model X v%d", i % 2);
    car.vin = String.format("1VXBR12EXCP%06d", i).getBytes();
    car.year = 2014 + i;
    car.registration = "California";

    LeatherTrim trim = new LeatherTrim();
    trim.colour = "black";
    car.optionalExtra = trim;

    Engine engine = new Engine();
    engine.capacity = 85.0f;
    engine.type = (i % 2) == 0 ? EngineType.ELECTRIC : EngineType.PETROL;
    engine.hasTurboCharger = false;
    car.engine = engine;

    if (i % 4 == 0) {
      Service service = new Service();
      service.date = 1374084640;
      service.mechanic = "Elon Musk";
      car.serviceHistory = Lists.newArrayList();
      car.serviceHistory.add(service);
    }

    return car;
  }

  private static class MyMapper extends Mapper<LongWritable, Text, Void, Car> {
    @Override
    public void run(Context context) throws IOException ,InterruptedException {
      for (int i = 0; i < 10; i++) {
        context.write(null, nextRecord(i));
      }
    }
  }

  private static class MyMapper2 extends Mapper<Void, Car, Void, Car> {
    @Override
    protected void map(Void key, Car car, Context context) throws IOException ,InterruptedException {
      // Note: Car can be null because of predicate pushdown defined by an UnboundedRecordFilter (see below)
      if (car != null) {
        context.write(null, car);
      }
    }

  }

  private static class MyMapperShort extends
      Mapper<Void, ShortCar, Void, ShortCar> {
    @Override
    protected void map(Void key, ShortCar car, Context context)
        throws IOException, InterruptedException {
      // Note: Car can be null because of predicate pushdown defined by an
      // UnboundedRecordFilter (see below)
      if (car != null) {
        context.write(null, car);
      }
    }

  }

  static class ElectricCarFilter implements UnboundRecordFilter {
    private final UnboundRecordFilter filter;

    ElectricCarFilter() {
      filter = ColumnRecordFilter.column("engine.type", ColumnPredicates.equalTo(org.apache.parquet.avro.EngineType.ELECTRIC));
    }

    @Override
    public RecordFilter bind(Iterable<ColumnReader> readers) {
      return filter.bind(readers);
    }
  }

  private final Configuration conf = new Configuration();
  private final Path inputPath = new Path("src/test/java/org/apache/parquet/avro/TestReflectInputOutputFormat.java");
  private final Path parquetPath = new Path("target/test/hadoop/TestReflectInputOutputFormat/parquet");
  private final Path outputPath = new Path("target/test/hadoop/TestReflectInputOutputFormat/out");

  @Before
  public void createParquetFile() throws Exception {
    // set up readers and writers not in MR
    conf.setBoolean(AvroReadSupport.AVRO_COMPATIBILITY, false);
    AvroReadSupport.setAvroDataSupplier(conf, ReflectDataSupplier.class);
    AvroWriteSupport.setAvroDataSupplier(conf, ReflectDataSupplier.class);

    final FileSystem fileSystem = parquetPath.getFileSystem(conf);
    fileSystem.delete(parquetPath, true);
    fileSystem.delete(outputPath, true);
    {
      final Job job = new Job(conf, "write");

      // input not really used
      TextInputFormat.addInputPath(job, inputPath);
      job.setInputFormatClass(TextInputFormat.class);

      job.setMapperClass(TestReflectInputOutputFormat.MyMapper.class);
      job.setNumReduceTasks(0);

      job.setOutputFormatClass(AvroParquetOutputFormat.class);
      AvroParquetOutputFormat.setOutputPath(job, parquetPath);
      AvroParquetOutputFormat.setSchema(job, CAR_SCHEMA);
      AvroParquetOutputFormat.setAvroDataSupplier(job, ReflectDataSupplier.class);

      waitForJob(job);
    }
  }

  @Test
  public void testReadWrite() throws Exception {

    conf.setBoolean(AvroReadSupport.AVRO_COMPATIBILITY, false);
    final Job job = new Job(conf, "read");
    job.setInputFormatClass(AvroParquetInputFormat.class);
    AvroParquetInputFormat.setInputPaths(job, parquetPath);
    // Test push-down predicates by using an electric car filter
    AvroParquetInputFormat.setUnboundRecordFilter(job, ElectricCarFilter.class);

    // Test schema projection by dropping the optional extras
    Schema projection = Schema.createRecord(CAR_SCHEMA.getName(),
        CAR_SCHEMA.getDoc(), CAR_SCHEMA.getNamespace(), false);
    List<Schema.Field> fields = Lists.newArrayList();
    for (Schema.Field field : ReflectData.get().getSchema(Car.class).getFields()) {
      if (!"optionalExtra".equals(field.name())) {
        fields.add(new Schema.Field(field.name(), field.schema(), field.doc(),
            field.defaultVal(), field.order()));
      }
    }
    projection.setFields(fields);
    AvroParquetInputFormat.setRequestedProjection(job, projection);

    job.setMapperClass(TestReflectInputOutputFormat.MyMapper2.class);
    job.setNumReduceTasks(0);

    job.setOutputFormatClass(AvroParquetOutputFormat.class);
    AvroParquetOutputFormat.setOutputPath(job, outputPath);
    AvroParquetOutputFormat.setSchema(job, CAR_SCHEMA);

    waitForJob(job);

    final Path mapperOutput = new Path(outputPath.toString(),
        "part-m-00000.parquet");
    try(final AvroParquetReader<Car> out = new AvroParquetReader<>(conf, mapperOutput)) {
      Car car;
      Car previousCar = null;
      int lineNumber = 0;
      while ((car = out.read()) != null) {
        if (previousCar != null) {
          // Testing reference equality here. The "model" field should be dictionary-encoded.
          assertSame(car.model, previousCar.model);
        }
        // Make sure that predicate push down worked as expected
        if (car.engine.type == EngineType.PETROL) {
          fail("UnboundRecordFilter failed to remove cars with PETROL engines");
        }
        // Note we use lineNumber * 2 because of predicate push down
        Car expectedCar = nextRecord(lineNumber * 2);
        // We removed the optional extra field using projection so we shouldn't
        // see it here...
        expectedCar.optionalExtra = null;
        assertEquals("line " + lineNumber, expectedCar, car);
        ++lineNumber;
        previousCar = car;
      }
    }
  }

  @Test
  public void testReadWriteChangedCar() throws Exception {

    conf.setBoolean(AvroReadSupport.AVRO_COMPATIBILITY, false);
    final Job job = new Job(conf, "read changed/short");
    job.setInputFormatClass(AvroParquetInputFormat.class);
    AvroParquetInputFormat.setInputPaths(job, parquetPath);
    // Test push-down predicates by using an electric car filter
    AvroParquetInputFormat.setUnboundRecordFilter(job, ElectricCarFilter.class);

    // Test schema projection by dropping the engine, year, and vin (like ShortCar),
    // but making make optional (unlike ShortCar)
    Schema projection = Schema.createRecord(CAR_SCHEMA.getName(),
        CAR_SCHEMA.getDoc(), CAR_SCHEMA.getNamespace(), false);
    List<Schema.Field> fields = Lists.newArrayList();
    for (Schema.Field field : CAR_SCHEMA.getFields()) {
      // No make!
      if ("engine".equals(field.name()) || "year".equals(field.name()) || "vin".equals(field.name())) {
        fields.add(new Schema.Field(field.name(), field.schema(), field.doc(),
            field.defaultVal(), field.order()));
      }
    }
    projection.setFields(fields);
    AvroParquetInputFormat.setRequestedProjection(job, projection);
    AvroParquetInputFormat.setAvroReadSchema(job, SHORT_CAR_SCHEMA);

    job.setMapperClass(TestReflectInputOutputFormat.MyMapperShort.class);
    job.setNumReduceTasks(0);

    job.setOutputFormatClass(AvroParquetOutputFormat.class);
    AvroParquetOutputFormat.setOutputPath(job, outputPath);
    AvroParquetOutputFormat.setSchema(job, SHORT_CAR_SCHEMA);

    waitForJob(job);

    final Path mapperOutput = new Path(outputPath.toString(), "part-m-00000.parquet");
    try(final AvroParquetReader<ShortCar> out = new AvroParquetReader<>(conf, mapperOutput)) {
      ShortCar car;
      int lineNumber = 0;
      while ((car = out.read()) != null) {
        // Make sure that predicate push down worked as expected
        // Note we use lineNumber * 2 because of predicate push down
        Car expectedCar = nextRecord(lineNumber * 2);
        // We removed the optional extra field using projection so we shouldn't see it here...
        assertNull(car.make);
        assertEquals(car.engine, expectedCar.engine);
        assertEquals(car.year, expectedCar.year);
        assertArrayEquals(car.vin, expectedCar.vin);
        ++lineNumber;
      }
    }
  }

  private void waitForJob(Job job) throws Exception {
    job.submit();
    while (!job.isComplete()) {
      LOG.debug("waiting for job {}", job.getJobName());
      sleep(100);
    }
    LOG.info("status for job {}: {}", job.getJobName(), (job.isSuccessful() ? "SUCCESS" : "FAILURE"));
    if (!job.isSuccessful()) {
      throw new RuntimeException("job failed " + job.getJobName());
    }
  }

  @After
  public void deleteOutputFile() throws IOException {
    final FileSystem fileSystem = parquetPath.getFileSystem(conf);
    fileSystem.delete(parquetPath, true);
    fileSystem.delete(outputPath, true);
  }
}
