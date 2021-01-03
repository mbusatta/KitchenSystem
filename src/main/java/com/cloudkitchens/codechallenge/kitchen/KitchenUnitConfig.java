package com.cloudkitchens.codechallenge.kitchen;

import com.typesafe.config.ConfigFactory;

import java.io.File;
import java.nio.file.Paths;

public class KitchenUnitConfig {

    private static final String CONF_FILE = "kitchen-unit.conf";

    public static final com.typesafe.config.Config config =
            ConfigFactory.parseFile(Paths.get(CONF_FILE).toFile());

}
