Pod::Spec.new do |s|
  s.name         = "ReactNativeBeaconsManager"
  s.version      = "1.7.0"
  s.summary      = "React-Native library for detecting beacons (iOS and Android)"
  s.homepage     = "https://github.com/newoceaninfosys/react-native-beacons-manager#readme"
  s.license      = { :type => "MIT" }
  s.authors      = { "" => "" }
  s.platform     = :ios, "9.0"
  s.source       = { :git => "https://github.com/newoceaninfosys/react-native-beacons-manager.git" }
  s.source_files = "ios", "ios/**/*.{h,m}"

  s.dependency 'React'
end
