angular.module('FermentationApp', []).controller('MonitorController', function($http, $interval) {
  var monitor = this;
  this.ambient = '--';

  $interval(this.update(), 5000);

  monitor.update = function() {
    monitor.ambient = '69.7';
    //$http.get('path').then(function(response){
      //monitor.
    //});
  }
});

