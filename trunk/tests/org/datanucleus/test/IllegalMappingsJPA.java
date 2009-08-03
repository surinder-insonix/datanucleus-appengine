/**********************************************************************
Copyright (c) 2009 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
**********************************************************************/
package org.datanucleus.test;

import org.datanucleus.jpa.annotations.Extension;
import org.datanucleus.jpa.annotations.Extensions;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

/**
 * @author Max Ross <maxr@google.com>
 */
public class IllegalMappingsJPA {

  @Entity
  public static class HasLongPkWithStringAncestor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Extension(vendorName = "datanucleus", key = "gae.parent-pk", value = "true")
    private String illegal;
  }

  @Entity
  public static class HasUnencodedStringPkWithStringAncestor {

    @Id
    public String id;

    @Extension(vendorName = "datanucleus", key = "gae.parent-pk", value = "true")
    private String illegal;
  }

  @Entity
  public static class HasMultiplePkNameFields {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Extension(vendorName = "datanucleus", key = "gae.encoded-pk", value = "true")
    private String id;

    @Extension(vendorName = "datanucleus", key = "gae.pk-name", value = "true")
    private String firstIsOk;

    @Extension(vendorName = "datanucleus", key = "gae.pk-name", value = "true")
    private String secondIsIllegal;
  }

  @Entity
  public static class HasMultiplePkIdFields {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Extension(vendorName = "datanucleus", key = "gae.encoded-pk", value = "true")
    private String id;

    @Extension(vendorName = "datanucleus", key = "gae.pk-id", value = "true")
    private Long firstIsOk;

    @Extension(vendorName = "datanucleus", key = "gae.pk-id", value = "true")
    private Long secondIsIllegal;
  }

  @Entity
  public static class MultipleAncestors {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Extension(vendorName = "datanucleus", key = "gae.encoded-pk", value = "true")
    private String id;

    @Extension(vendorName = "datanucleus", key = "gae.parent-pk", value = "true")
    private String firstIsOk;

    @Extension(vendorName = "datanucleus", key = "gae.parent-pk", value = "true")
    private String secondIsIllegal;
  }

  @Entity
  public static class EncodedPkOnNonPrimaryKeyField {

    @Id
    public String id;

    @Extension(vendorName = "datanucleus", key = "gae.encoded-pk", value = "true")
    private String illegal;
  }

  @Entity
  public static class EncodedPkOnNonStringPrimaryKeyField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Extension(vendorName = "datanucleus", key = "gae.encoded-pk", value = "true")
    private Long id;
  }

  @Entity
  public static class PkNameOnNonStringField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Extension(vendorName = "datanucleus", key = "gae.encoded-pk", value = "true")
    private String id;

    @Extension(vendorName = "datanucleus", key = "gae.pk-name", value = "true")
    private Long illegal;
  }

  @Entity
  public static class PkIdOnNonLongField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Extension(vendorName = "datanucleus", key = "gae.encoded-pk", value = "true")
    private String id;

    @Extension(vendorName = "datanucleus", key = "gae.pk-id", value = "true")
    private String illegal;
  }

  @Entity
  public static class PkMarkedAsAncestor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Extensions({
      @Extension(vendorName = "datanucleus", key = "gae.encoded-pk", value = "true"),
      @Extension(vendorName = "datanucleus", key = "gae.parent-pk", value = "true")}
    )
    private String illegal;
  }

  @Entity
  public static class PkMarkedAsPkId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Extension(vendorName = "datanucleus", key = "gae.pk-id", value = "true")
    private Long illegal;
  }

  @Entity
  public static class PkMarkedAsPkName {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Extension(vendorName = "datanucleus", key = "gae.pk-name", value = "true")
    private String illegal;
  }

  @Entity
  public static class PkIdWithUnencodedStringPrimaryKey {

    @Id
    public String id;

    @Extension(vendorName = "datanucleus", key = "gae.pk-id", value = "true")
    private Long illegal;
  }

  @Entity
  public static class PkNameWithUnencodedStringPrimaryKey {

    @Id
    public String id;

    @Extension(vendorName = "datanucleus", key = "gae.pk-name", value = "true")
    private String illegal;
  }

  @Entity
  public static class OneToManyParentWithRootOnlyLongUniChild {
    @Id
    public String id;

    @OneToMany(cascade = CascadeType.ALL)
    private List<HasLongPkJPA> uniChildren = new ArrayList<HasLongPkJPA>();
  }

  @Entity
  public static class OneToManyParentWithRootOnlyLongBiChild {
    @Id
    public String id;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<RootOnlyLongBiOneToManyChild> biChildren = new ArrayList<RootOnlyLongBiOneToManyChild>();
  }

  @Entity
  public static class RootOnlyLongBiOneToManyChild {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private OneToManyParentWithRootOnlyLongBiChild parent;
  }

  @Entity
  public static class OneToManyParentWithRootOnlyStringUniChild {
    @Id
    public String id;

    @OneToMany(cascade = CascadeType.ALL)
    private List<HasUnencodedStringPkJPA> uniChildren = new ArrayList<HasUnencodedStringPkJPA>();
  }

  @Entity
  public static class OneToManyParentWithRootOnlyStringBiChild {
    @Id
    public String id;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<RootOnlyStringBiOneToManyChild> biChildren = new ArrayList<RootOnlyStringBiOneToManyChild>();
  }

  @Entity
  public static class RootOnlyStringBiOneToManyChild {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.EAGER)
    private OneToManyParentWithRootOnlyStringBiChild parent;
  }

  @Entity
  public static class OneToOneParentWithRootOnlyLongUniChild {
    @Id
    public String id;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private HasLongPkJPA uniChild;
  }

  @Entity
  public static class OneToOneParentWithRootOnlyLongBiChild {
    @Id
    public String id;

    @OneToOne(fetch = FetchType.LAZY)
    private RootOnlyLongBiOneToOneChild biChild;
  }

  @Entity
  public static class RootOnlyLongBiOneToOneChild {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @OneToOne(mappedBy = "biChild", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private OneToOneParentWithRootOnlyLongBiChild parent;
  }

  @Entity
  public static class OneToOneParentWithRootOnlyStringUniChild {
    @Id
    public String id;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private HasUnencodedStringPkJPA uniChild;
  }

  @Entity
  public static class OneToOneParentWithRootOnlyStringBiChild {
    @Id
    public String id;

    @OneToOne(fetch = FetchType.LAZY)
    private RootOnlyStringBiOneToOneChild biChild;
  }

  @Entity
  public static class RootOnlyStringBiOneToOneChild {
    @Id
    private String id;

    @OneToOne(mappedBy = "biChild", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private OneToOneParentWithRootOnlyStringBiChild parent;
  }

  @Entity
  public static class LongParent {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Extension(vendorName = "datanucleus", key="gae.parent-pk", value="true")
    private Long illegal;
  }
}
